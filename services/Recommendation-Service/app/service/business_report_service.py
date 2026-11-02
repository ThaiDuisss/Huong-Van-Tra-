from datetime import datetime, timedelta, timezone
import hashlib
import logging
from uuid import uuid4

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from app.clients.file_client import FileClient
from app.core.config import get_settings
from app.models.business_report import BusinessReportExport
from app.schemas.business_report import BusinessExportResponse
from app.service.business_report_workbook import build_workbook
from app.service.recommendation_snapshot_service import utc_now
from app.service.report_data_source import OPERATIONAL, build_offline, select_source
from app.service.bom_report_source import build_bom, select_bom_source

logger = logging.getLogger(__name__)


class BusinessReportService:
    def __init__(self, files=None):
        self.files = files or FileClient()

    def require(self, db, export_id):
        # Same established seven-role staff-sharing policy as original AI reports.
        record = db.get(BusinessReportExport, export_id)
        if not record or record.status != "COMPLETE":
            raise HTTPException(404, "Generated report not found")
        return record

    @staticmethod
    def response(record):
        extra = {}
        if (record.evidence or {}).get("report_type") == "CUSTOM_BOM":
            extra = dict(reportType="CUSTOM_BOM", sourceKind="SYNTHETIC", researchOnly=True,
                         productionApproved=False, createdBy=record.created_by, status="COMPLETE",
                         provenance=record.evidence["report_source"])
        return BusinessExportResponse(id=record.id, fileId=record.file_id, fileName=record.file_name,
            fromDate=record.from_date, toDate=record.to_date, generatedAt=record.generated_at, sha256=record.sha256, **extra)

    def export(self, db, payload, user_id):
        cfg = get_settings()
        if payload.reportType == "PRODUCT" and (payload.toDate - payload.fromDate).days + 1 > cfg.ai_report_max_period_days:
            raise HTTPException(422, "Reporting period exceeds maximum")
        today = datetime.now(timezone(timedelta(hours=7))).date()
        if payload.reportType == "PRODUCT" and payload.toDate > today:
            raise HTTPException(422, "Future reporting dates are not supported")
        def intent():
            return db.scalar(select(BusinessReportExport).where(BusinessReportExport.created_by == user_id,
                BusinessReportExport.request_key == str(payload.requestId)))
        record = intent()
        if not record:
            source = ({"report_type": "CUSTOM_BOM", "report_source": select_bom_source(cfg)}
                      if payload.reportType == "CUSTOM_BOM" else {"report_source": select_source(cfg)})
            record = BusinessReportExport(id=str(uuid4()), created_by=user_id, request_key=str(payload.requestId),
                from_date=payload.fromDate, to_date=payload.toDate, generated_at=utc_now(), status="PENDING",
                evidence=source,
                file_name=f"AI_Report_HuongVanTra_{payload.fromDate:%Y%m%d}_{payload.toDate:%Y%m%d}.xlsx")
            db.add(record)
            try:
                db.commit()
            except IntegrityError:
                db.rollback(); record = intent()
                if not record:
                    raise HTTPException(409, "Concurrent export conflict") from None
        if (record.evidence or {}).get("report_type", "PRODUCT") != payload.reportType:
            raise HTTPException(409, "Export request ID belongs to a different report type")
        if (record.from_date, record.to_date) != (payload.fromDate, payload.toDate):
            raise HTTPException(409, "Export request ID belongs to a different period")
        if record.status == "COMPLETE":
            return self.response(record)
        # Serialize same-intent preparation. Frozen bytes commit BEFORE remote writes.
        record = db.scalar(select(BusinessReportExport).where(BusinessReportExport.id == record.id)
                           .with_for_update().execution_options(populate_existing=True))
        if ((record.evidence or {}).get("report_type", "PRODUCT") != payload.reportType
                or (record.from_date, record.to_date) != (payload.fromDate, payload.toDate)):
            db.rollback()
            raise HTTPException(409, "Export identity changed while preparing report")
        if record.status == "COMPLETE":
            db.commit()
            return self.response(record)
        if not record.workbook:
            # Reset observation cutoff after obtaining the preparation lock.
            record.generated_at = utc_now()
            try:
                # Legacy pending intents predate source selection and belong to OPERATIONAL.
                pinned = (record.evidence or {}).get("report_source", dict(OPERATIONAL))
                if payload.reportType == "CUSTOM_BOM":
                    workbook, evidence = build_bom(cfg, pinned)
                elif pinned["kind"] == "OPERATIONAL":
                    workbook, evidence = build_workbook(db, record.from_date, record.to_date, record.generated_at,
                                                        cfg.ai_report_max_evidence_rows)
                    evidence["report_source"] = pinned
                elif pinned["kind"] == "OFFLINE_EVALUATION":
                    workbook, evidence = build_offline(cfg, pinned, record.from_date, record.to_date,
                                                       record.generated_at, cfg.ai_report_max_evidence_rows)
                else:
                    raise HTTPException(409, "Export intent has an unsupported report source")
            except Exception:
                db.rollback()
                raise
            if len(workbook) > 10 * 1024 * 1024:
                db.rollback(); raise HTTPException(422, "Report too large; select a shorter period")
            record.workbook, record.evidence = workbook, evidence
            record.sha256 = hashlib.sha256(workbook).hexdigest()
            record.status = "PREPARED"
            db.commit()
        else:
            db.commit()
        try:
            metadata = self.files.store_generated(record)
            if (str(metadata.fileId), str(metadata.ownerId), metadata.fileType, metadata.visibility,
                    metadata.status, metadata.sizeBytes, metadata.fileName, metadata.deleted) != (
                    record.id, user_id, "XLSX", "PRIVATE", "ACTIVE", len(record.workbook), record.file_name, False):
                raise HTTPException(502, "Generated file metadata mismatch")
            record.file_id, record.status, record.last_error = str(metadata.fileId), "COMPLETE", None
            db.commit()
            return self.response(record)
        except Exception:
            db.rollback()
            logger.error("Generated AI report storage/finalization failed; durable retry available")
            raise

    def download(self, db, export_id):
        record = self.require(db, export_id)
        from types import SimpleNamespace
        return self.files.download(SimpleNamespace(id=record.id, file_id=record.file_id, file_type="XLSX"), generated=True)
