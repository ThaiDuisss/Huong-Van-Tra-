from sqlalchemy.dialects.mysql import insert
from sqlalchemy.orm import Session

from app.models.product_variant import ProductVariant


class ProductVariantRepository:
    def upsert(
        self,
        db: Session,
        variant: ProductVariant,
    ) -> None:
        stmt = insert(ProductVariant).values(
            id=variant.id,
            product_id=variant.product_id,
            sku=variant.sku,
            variant_name=variant.variant_name,
            selling_price=variant.selling_price,
            status=variant.status,
            is_deleted=variant.is_deleted,
        )

        stmt = stmt.on_duplicate_key_update(
            product_id=stmt.inserted.product_id,
            sku=stmt.inserted.sku,
            variant_name=stmt.inserted.variant_name,
            selling_price=stmt.inserted.selling_price,
            status=stmt.inserted.status,
            is_deleted=stmt.inserted.is_deleted,
        )

        db.execute(stmt)

    def find_by_id(
        self,
        db: Session,
        variant_id: str,
    ) -> ProductVariant | None:
        return db.get(ProductVariant, variant_id)
