from sqlalchemy.orm import Session

from app.models.product_variant import ProductVariant
from app.repositories.product_variant_repository import (
    ProductVariantRepository,
)


class ProductResolutionError(Exception):
    """Raised when an external product variant or SKU cannot be resolved to a local product_id."""


class ProductResolver:
    def __init__(
        self,
        variant_repository: ProductVariantRepository,
    ) -> None:
        self.variant_repository = variant_repository

    def resolve_variant(
        self,
        db: Session,
        product_variant_id: str | None = None,
        sku: str | None = None,
    ) -> ProductVariant | None:
        if product_variant_id:
            variant = self.variant_repository.find_by_id(db, product_variant_id)
            if variant is not None:
                return variant

        if sku:
            variant = self.variant_repository.find_by_sku(db, sku)
            if variant is not None:
                return variant

        return None

    def resolve_product_id(
        self,
        db: Session,
        product_variant_id: str | None = None,
        sku: str | None = None,
    ) -> str | None:
        variant = self.resolve_variant(
            db,
            product_variant_id=product_variant_id,
            sku=sku,
        )
        if variant is None:
            return None
        return variant.product_id