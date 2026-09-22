from datetime import datetime
from decimal import Decimal

from sqlalchemy import Index, Integer, Numeric, String, text
from sqlalchemy.dialects.mysql import DATETIME
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base, TimestampMixin


class CustomerInteraction(TimestampMixin, Base):
    __tablename__ = "customer_interactions"
    __table_args__ = (
        Index("idx_customer_interactions_product_id", "product_id"),
    )

    customer_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    product_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    total_purchase_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        server_default=text("0"),
    )
    total_purchase_quantity: Mapped[Decimal] = mapped_column(
        Numeric(14, 3),
        nullable=False,
        server_default=text("0.000"),
    )
    total_return_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        server_default=text("0"),
    )
    total_return_quantity: Mapped[Decimal] = mapped_column(
        Numeric(14, 3),
        nullable=False,
        server_default=text("0.000"),
    )
    last_interaction_time: Mapped[datetime] = mapped_column(
        DATETIME(fsp=6),
        nullable=False,
    )
