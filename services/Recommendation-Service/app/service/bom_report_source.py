"""Trusted, lazy research artifact adapter for official AI report exports.

No demo HTTP/token/auth mode, Product history, training, or filesystem persistence.
Persistence is the existing BusinessReportExport/File-Service lifecycle.
"""
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
from io import BytesIO
from pathlib import Path
import re

from fastapi import HTTPException
from openpyxl import load_workbook

from app.ingredient_demo.bom_report import PROTOCOL
from app.ingredient_demo.service import IngredientDemoService
from app.ingredient_demo.workbook import WORKBOOK_SCHEMA, build_workbook, verify_workbook
from app.ingredient_poc.synthetic import END, T2
from app.schemas.business_report import BOM_FROM, BOM_TO


class BomExportDataError(HTTPException):
    def __init__(self, code: str, message: str, details: dict[str, str] | None = None):
        super().__init__(status_code=422, detail=message)
        self.code = code
        self.details = details


def require_bom_coverage(from_date: date, to_date: date):
    if from_date < BOM_FROM or to_date > BOM_TO:
        raise BomExportDataError(
            "BOM_PERIOD_UNSUPPORTED",
            "The research dataset cannot evaluate the full requested BOM period.",
            {"fromDate": BOM_FROM.isoformat(), "toDate": BOM_TO.isoformat(), "timezone": "UTC"},
        )


@dataclass(frozen=True, repr=False)
class ReportArtifacts:
    dataset_path: Path
    dataset_sha256: str
    dataset_id: str
    model_path: Path
    model_sha256: str


def load_research(cfg):
    if not cfg.ai_report_bom_enabled:
        raise HTTPException(503, "Official BOM research export is not configured")
    try:
        artifacts = ReportArtifacts(
            Path(cfg.ai_report_bom_dataset_path or ""), cfg.ai_report_bom_dataset_sha256 or "",
            cfg.ai_report_bom_dataset_id or "", Path(cfg.ai_report_bom_model_path or ""),
            cfg.ai_report_bom_model_sha256 or "")
        if (not artifacts.dataset_id or not artifacts.dataset_path.is_absolute() or not artifacts.model_path.is_absolute()
                or not re.fullmatch(r"[0-9a-f]{64}", artifacts.dataset_sha256)
                or not re.fullmatch(r"[0-9a-f]{64}", artifacts.model_sha256)):
            raise ValueError("Invalid research artifact configuration")
        service = IngredientDemoService(artifacts)
        service.verify_integrity()
        return service, artifacts
    except Exception:
        raise HTTPException(503, "Verified BOM dataset/model unavailable or incompatible") from None


def descriptor(service, artifacts):
    return {"kind": "SYNTHETIC_BOM_RESEARCH", "source_kind": "SYNTHETIC", "research_only": True,
            "production_approved": False, "dataset_id": artifacts.dataset_id,
            "dataset_sha256": artifacts.dataset_sha256, "model_sha256": artifacts.model_sha256,
            "model_version": service._model.metadata["model_version"],
            "model_fingerprint": service._model.payload()["model_fingerprint"],
            "source_snapshot_hash": service.data.dataset["source_snapshot_hash"],
            "protocol": {"name": PROTOCOL, "mode": "CUSTOM_BOM", "policy": "REPEAT", "top_k": 5,
                         "as_of": T2.isoformat(), "label_end_exclusive": END.isoformat()}}


def select_bom_source(cfg):
    return descriptor(*load_research(cfg))


def build_bom(cfg, pinned, from_date=BOM_FROM, to_date=BOM_TO):
    require_bom_coverage(from_date, to_date)
    service, artifacts = load_research(cfg)
    if descriptor(service, artifacts) != pinned:
        raise HTTPException(409, "BOM export is pinned to different artifacts; restore them or use a new requestId")
    try:
        start = datetime.combine(from_date, time.min, timezone.utc)
        end = datetime.combine(to_date + timedelta(days=1), time.min, timezone.utc)
        snapshot = service.report_snapshot(start, end)
        if not snapshot["summary"]["test_bom_sale_rows"]:
            service.verify_integrity()
            raise BomExportDataError("BOM_NO_DATA", "No BOM purchases in the requested period.")
        content = build_workbook(service.data, service._model.metadata, snapshot)
        service.verify_integrity()
        workbook = load_workbook(BytesIO(content))
        try:
            workbook_evidence = verify_workbook(workbook, service.data, snapshot)
        finally:
            workbook.close()
        return content, {"report_type": "CUSTOM_BOM", "report_source": pinned,
                         "protocol": snapshot["protocol"], "summary": snapshot["summary"],
                         "workbook_schema": WORKBOOK_SCHEMA, "workbook_evidence": workbook_evidence}
    except BomExportDataError:
        raise
    except Exception:
        raise HTTPException(503, "BOM research workbook verification failed") from None
