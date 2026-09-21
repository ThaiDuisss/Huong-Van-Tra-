from sqlalchemy.orm import Session

from app.repositories.product_variant_repository import (
    ProductVariantRepository,
)


class ProductResolver:
    def __init__(
        self,
        variant_repository: ProductVariantRepository,
    ) -> None:
        self.variant_repository = variant_repository

    def resolve_product_id(
        self,
        db: Session,
        product_variant_id: str,
    ) -> str | None:

        variant = self.variant_repository.find_by_id(
            db,
            product_variant_id,
        )

        if variant is None:
            return None

        return variant.product_id