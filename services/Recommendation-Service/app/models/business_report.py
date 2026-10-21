"""Immutable serving/purchase evidence and durable generated-export intents.

All DATETIME values are UTC. No UI impression is inferred from a response.
"""
from datetime import date, datetime

from sqlalchemy import Date, DateTime, Index, Integer, JSON, LargeBinary, String, UniqueConstraint
from sqlalchemy.dialects.mysql import DATETIME, LONGBLOB
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class RecommendationSnapshot(Base):
    __tablename__ = "recommendation_snapshots"
    __table_args__ = (
        UniqueConstraint("customer_id", "request_key", name="uq_snapshot_customer_request"),
        Index("idx_snapshot_customer_time", "customer_id", "generated_at"),
        Index("idx_snapshot_generated", "generated_at"),
    )
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    customer_id: Mapped[str] = mapped_column(String(36))
    request_key: Mapped[str] = mapped_column(String(36))
    request_hash: Mapped[str] = mapped_column(String(64))
    generated_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6))
    observed_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6))
    mode: Mapped[str] = mapped_column(String(20))
    source: Mapped[str] = mapped_column(String(20))
    fallback_reason: Mapped[str | None] = mapped_column(String(100), nullable=True)
    evaluation_days: Mapped[int] = mapped_column(Integer)
    context: Mapped[dict] = mapped_column(JSON)
    response: Mapped[dict] = mapped_column(JSON)


class CompletedPurchaseOrder(Base):
    """Whole validated ORDER_COMPLETED/COMPLETED/PAID event, canonical lines.

    A second transport event for the same order cannot add quantities again.
    No PII from the upstream order is copied.
    """
    __tablename__ = "report_purchase_orders"
    __table_args__ = (Index("idx_report_order_customer_time", "customer_id", "occurred_at"),
                      Index("idx_report_order_time", "occurred_at"))
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    source_event_id: Mapped[str] = mapped_column(String(36))
    customer_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    occurred_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6))
    observed_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6))
    evidence_hash: Mapped[str] = mapped_column(String(64))
    lines: Mapped[list] = mapped_column(JSON)


class BusinessReportExport(Base):
    __tablename__ = "business_report_exports"
    __table_args__ = (UniqueConstraint("created_by", "request_key", name="uq_business_export_actor_request"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    created_by: Mapped[str] = mapped_column(String(36))
    request_key: Mapped[str] = mapped_column(String(36))
    from_date: Mapped[date] = mapped_column(Date)
    to_date: Mapped[date] = mapped_column(Date)
    generated_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6))
    status: Mapped[str] = mapped_column(String(20))
    file_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    file_name: Mapped[str] = mapped_column(String(255))
    sha256: Mapped[str | None] = mapped_column(String(64), nullable=True)
    # Frozen bytes survive remote timeouts, process restarts and changing ingestion.
    workbook: Mapped[bytes | None] = mapped_column(LargeBinary().with_variant(LONGBLOB(), "mysql"), nullable=True)
    evidence: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    last_error: Mapped[str | None] = mapped_column(String(40), nullable=True)
