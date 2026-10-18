from datetime import datetime

from sqlalchemy import Boolean, DateTime, Index, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class AiReport(Base):
    __tablename__ = "ai_reports"
    __table_args__ = (
        UniqueConstraint("file_id", name="uq_ai_reports_file_id"),
        Index("idx_ai_reports_updated_id", "updated_at", "id"),
    )
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    name: Mapped[str] = mapped_column(String(200))
    version: Mapped[str] = mapped_column(String(50))
    file_id: Mapped[str] = mapped_column(String(36))
    file_name: Mapped[str] = mapped_column(String(255))
    file_type: Mapped[str] = mapped_column(String(10))
    created_by: Mapped[str] = mapped_column(String(36))
    updated_by: Mapped[str | None] = mapped_column(String(36), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime)
    updated_at: Mapped[datetime] = mapped_column(DateTime)
    is_deleted: Mapped[bool] = mapped_column(Boolean, default=False)


class AiReportImport(Base):
    """Durable create intent. No document bytes or signed URLs are persisted here."""
    __tablename__ = "ai_report_imports"
    __table_args__ = (
        UniqueConstraint("file_id", "created_by", name="uq_ai_report_imports_file_actor"),
        Index("idx_ai_report_imports_status_time", "status", "updated_at"),
    )
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    file_id: Mapped[str] = mapped_column(String(36))
    name: Mapped[str] = mapped_column(String(200))
    version: Mapped[str] = mapped_column(String(50))
    created_by: Mapped[str] = mapped_column(String(36))
    status: Mapped[str] = mapped_column(String(20), default="PENDING")
    last_error: Mapped[str | None] = mapped_column(String(50), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime)
    updated_at: Mapped[datetime] = mapped_column(DateTime)
