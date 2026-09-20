from datetime import datetime
from decimal import Decimal
from enum import StrEnum
from uuid import uuid4

from sqlalchemy import Index, Numeric, String, UniqueConstraint, text
from sqlalchemy.dialects.mysql import DATETIME
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class InteractionEventType(StrEnum):
    PURCHASE = "PURCHASE"
    RETURN = "RETURN"


class InteractionEvent(Base):
    __tablename__ = "interaction_events"
    __table_args__ = (
        UniqueConstraint(
            "source_event_id",
            "order_item_id",
            "event_type",
            name="uq_interaction_events_source_line",
        ),
        Index("idx_interaction_events_source_event_id", "source_event_id"),
        Index("idx_interaction_events_customer_time", "customer_id", "event_time"),
        Index("idx_interaction_events_product_time", "product_id", "event_time"),
        Index("idx_interaction_events_type_time", "event_type", "event_time"),
        Index("idx_interaction_events_return_item_id", "return_item_id"),
    )

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid4()),
    )
    source_event_id: Mapped[str] = mapped_column(String(36), nullable=False)
    order_item_id: Mapped[str] = mapped_column(String(36), nullable=False)
    return_item_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    customer_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    product_variant_id: Mapped[str] = mapped_column(String(36), nullable=False)
    product_id: Mapped[str] = mapped_column(String(36), nullable=False)
    event_type: Mapped[str] = mapped_column(String(30), nullable=False)
    quantity: Mapped[Decimal] = mapped_column(Numeric(14, 3), nullable=False)
    unit_price: Mapped[Decimal | None] = mapped_column(Numeric(14, 2), nullable=True)
    order_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    return_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    return_reason: Mapped[str | None] = mapped_column(String(100), nullable=True)
    event_time: Mapped[datetime] = mapped_column(DATETIME(fsp=6), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DATETIME(fsp=6),
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP(6)"),
    )
