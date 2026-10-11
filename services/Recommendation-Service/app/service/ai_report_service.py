from datetime import datetime, timezone
from uuid import uuid4

from fastapi import HTTPException
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.clients.file_client import FILE_MIMES, FileClient
from app.models.ai_report import AiReport, AiReportImport
from app.repositories.ai_report_repository import AiReportRepository
from app.schemas.ai_report import AiReportCreate, AiReportMetadata, AiReportPage, AiReportResponse


def now():
    return datetime.now(timezone.utc).replace(tzinfo=None)


class AiReportService:
    def __init__(self, files: FileClient | None = None):
        self.files = files or FileClient()
        self.repo = AiReportRepository()

    def list(self, db: Session, page: int, size: int):
        rows, total = self.repo.page(db, page, size)
        pages = (total + size - 1) // size
        return AiReportPage(content=[AiReportResponse.model_validate(row) for row in rows],
            page=page, size=size, totalElements=total, totalPages=pages, first=page == 0,
            last=page + 1 >= pages)

    def require(self, db: Session, report_id: str):
        report = self.repo.visible(db, report_id)
        if not report:
            raise HTTPException(404, "AI Report not found")
        return report

    def create(self, db: Session, payload: AiReportCreate, user_id: str):
        file_id = str(payload.fileId)
        existing = self.repo.by_file(db, file_id)
        if existing:
            if existing.is_deleted or existing.created_by != user_id:
                raise HTTPException(409, "File already belongs to a report")
            # Match the original intent, even if metadata has since been edited.
            intent = self.repo.intent(db, file_id, user_id)
            if not intent or (intent.name, intent.version) != (payload.name, payload.version):
                raise HTTPException(409, "Retry payload differs from original import")
            return existing, False
        intent = self.repo.intent(db, file_id, user_id)
        if not intent:
            intent = AiReportImport(id=str(uuid4()), file_id=file_id, name=payload.name,
                version=payload.version, created_by=user_id, status="PENDING", created_at=now(), updated_at=now())
            db.add(intent)
            try:
                db.commit()  # Durable before either remote operation.
            except IntegrityError:
                db.rollback()
                intent = self.repo.intent(db, file_id, user_id)
                if not intent:
                    raise
        if intent.created_by != user_id:
            raise HTTPException(409, "File has another import in progress")
        if (intent.name, intent.version) != (payload.name, payload.version):
            raise HTTPException(409, "Retry payload differs from original import")
        if intent.status == "FAILED":
            intent.status, intent.last_error, intent.updated_at = "PENDING", None, now()
            db.commit()
        try:
            metadata = self.files.metadata(file_id, user_id)
            if str(metadata.fileId) != file_id or str(metadata.ownerId) != user_id:
                raise HTTPException(403, "File owner mismatch")
            if metadata.deleted or metadata.status != "ACTIVE" or metadata.visibility != "PRIVATE":
                raise HTTPException(409, "File is unavailable")
            if metadata.fileType not in FILE_MIMES or metadata.contentType != FILE_MIMES[metadata.fileType]:
                raise HTTPException(415, "Only PDF/XLSX reports are supported")
            self.files.bind(intent.id, file_id, user_id)
            report = AiReport(id=intent.id, name=intent.name, version=intent.version,
                file_id=file_id, file_name=metadata.fileName, file_type=metadata.fileType,
                created_by=user_id, updated_by=None, created_at=now(), updated_at=now(), is_deleted=False)
            db.add(report)
            intent.status, intent.last_error, intent.updated_at = "COMPLETE", None, now()
            db.commit()
            return report, True
        except HTTPException as error:
            db.rollback()
            intent = self.repo.intent(db, file_id, user_id, lock=True)
            if intent.status == "COMPLETE":
                # A concurrent successful request wins; do not overwrite its state.
                existing = self.repo.by_file(db, file_id)
                if existing and existing.id == intent.id and not existing.is_deleted:
                    return existing, False
                raise
            intent.status = "FAILED" if error.status_code < 500 else "PENDING"
            intent.last_error, intent.updated_at = f"HTTP_{error.status_code}", now()
            db.commit()
            raise
        except IntegrityError:
            db.rollback()
            existing = self.repo.by_file(db, file_id)
            if existing and existing.id == intent.id and not existing.is_deleted:
                return existing, False
            raise HTTPException(409, "Concurrent import conflict") from None
        except Exception:
            db.rollback()  # The intent survives; remote binding may have committed.
            raise

    def update(self, db: Session, report_id: str, payload: AiReportMetadata, user_id: str):
        report = self.require(db, report_id)
        report.name, report.version = payload.name, payload.version
        report.updated_by, report.updated_at = user_id, now()
        db.commit()
        return report
