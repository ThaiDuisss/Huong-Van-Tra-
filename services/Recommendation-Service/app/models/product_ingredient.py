from uuid import uuid4

from sqlalchemy import Boolean, Index, Integer, String, UniqueConstraint, text
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base, TimestampMixin


class ProductIngredient(TimestampMixin, Base):
    __tablename__ = "product_ingredients"
    __table_args__ = (
        UniqueConstraint(
            "product_id",
            "ingredient_id",
            "source_variant_id",
            "bom_version",
            name="uq_product_ingredients_source",
        ),
        Index("idx_product_ingredients_product_id", "product_id"),
        Index("idx_product_ingredients_ingredient_id", "ingredient_id"),
        Index("idx_product_ingredients_source_variant_id", "source_variant_id"),
    )

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid4()),
    )
    product_id: Mapped[str] = mapped_column(String(36), nullable=False)
    ingredient_id: Mapped[str] = mapped_column(String(36), nullable=False)
    ingredient_code: Mapped[str] = mapped_column(String(100), nullable=False)
    source_variant_id: Mapped[str] = mapped_column(String(36), nullable=False)
    bom_version: Mapped[int] = mapped_column(Integer, nullable=False)
    is_active_bom: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        server_default=text("1"),
    )

