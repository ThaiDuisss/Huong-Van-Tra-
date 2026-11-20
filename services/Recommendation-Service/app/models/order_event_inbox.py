"""Verified Kafka evidence and exact-line return history."""
from datetime import datetime
from decimal import Decimal

from sqlalchemy import BigInteger, ForeignKey, Index, Integer, JSON, Numeric, String
from sqlalchemy.dialects.mysql import DATETIME
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class OrderEventStream(Base):
    """A row locked for each order before purchase or return effects."""
    __tablename__ = "order_event_streams"

    order_id: Mapped[str] = mapped_column(String(36), primary_key=True)


class OrderEventInbox(Base):
    __tablename__ = "order_event_inbox"
    __table_args__ = (
        Index("idx_order_inbox_pending", "order_id", "status"),
    )

    event_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    order_id: Mapped[str] = mapped_column(ForeignKey("order_event_streams.order_id"))
    topic: Mapped[str] = mapped_column(String(249))
    partition_id: Mapped[int] = mapped_column(Integer)
    record_offset: Mapped[int] = mapped_column(BigInteger)
    event_type: Mapped[str] = mapped_column(String(32))
    fingerprint: Mapped[str] = mapped_column(String(64))
    key_id: Mapped[str] = mapped_column(String(64))
    key_version: Mapped[str] = mapped_column(String(64))
    receipt: Mapped[dict] = mapped_column(JSON)
    status: Mapped[str] = mapped_column(String(20))
    error_code: Mapped[str | None] = mapped_column(String(48))
    observed_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6))


class OrderEventDelivery(Base):
    __tablename__ = "order_event_deliveries"

    topic: Mapped[str] = mapped_column(String(249), primary_key=True)
    partition_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    record_offset: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    event_id: Mapped[str] = mapped_column(ForeignKey("order_event_inbox.event_id"))


class OrderReturnLine(Base):
    __tablename__ = "order_return_lines"
    __table_args__ = (
        Index("idx_order_return_lines_order", "order_id", "order_item_id"),
        Index("idx_order_return_lines_history", "customer_id", "occurred_at"),
    )

    return_item_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    event_id: Mapped[str] = mapped_column(ForeignKey("order_event_inbox.event_id"))
    return_id: Mapped[str] = mapped_column(String(36))
    order_id: Mapped[str] = mapped_column(ForeignKey("order_event_streams.order_id"))
    order_item_id: Mapped[str] = mapped_column(String(36))
    customer_id: Mapped[str | None] = mapped_column(String(36))
    product_variant_id: Mapped[str] = mapped_column(String(36))
    purchase_type: Mapped[str] = mapped_column(String(24))
    quantity: Mapped[Decimal] = mapped_column(Numeric(14, 3))
    reason: Mapped[str] = mapped_column(String(32))
    occurred_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6))
    observed_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6))
