from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.ai_report import AiReport, AiReportImport


class AiReportRepository:
    def visible(self, db: Session, report_id: str) -> AiReport | None:
        return db.scalar(select(AiReport).where(AiReport.id == report_id, AiReport.is_deleted.is_(False)))

    def by_file(self, db: Session, file_id: str) -> AiReport | None:
        return db.scalar(select(AiReport).where(AiReport.file_id == file_id))

    def intent(self, db: Session, file_id: str, user_id: str, lock: bool = False) -> AiReportImport | None:
        query = select(AiReportImport).where(AiReportImport.file_id == file_id,
            AiReportImport.created_by == user_id)
        return db.scalar(query.with_for_update() if lock else query)

    def page(self, db: Session, page: int, size: int) -> tuple[list[AiReport], int]:
        criteria = AiReport.is_deleted.is_(False)
        total = db.scalar(select(func.count()).select_from(AiReport).where(criteria)) or 0
        rows = db.scalars(select(AiReport).where(criteria).order_by(
            AiReport.updated_at.desc(), AiReport.id.desc(),
        ).offset(page * size).limit(size)).all()
        return list(rows), total
