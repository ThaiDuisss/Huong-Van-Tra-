from sqlalchemy import update
from sqlalchemy.dialects.mysql import insert
from sqlalchemy.orm import Session

from app.models.product_ingredient import ProductIngredient


class ProductIngredientRepository:
    def upsert(
        self,
        db: Session,
        product_ingredient: ProductIngredient,
    ) -> None:
        stmt = insert(ProductIngredient).values(
            id=product_ingredient.id,
            product_id=product_ingredient.product_id,
            ingredient_id=product_ingredient.ingredient_id,
            ingredient_code=product_ingredient.ingredient_code,
            source_variant_id=product_ingredient.source_variant_id,
            bom_version=product_ingredient.bom_version,
            is_active_bom=product_ingredient.is_active_bom,
        )

        stmt = stmt.on_duplicate_key_update(
            ingredient_code=stmt.inserted.ingredient_code,
            is_active_bom=stmt.inserted.is_active_bom,
        )

        db.execute(stmt)

    def deactivate_old_versions(
        self,
        db: Session,
        source_variant_id: str,
        active_bom_version: int,
    ) -> int:
        stmt = (
            update(ProductIngredient)
            .where(
                ProductIngredient.source_variant_id == source_variant_id,
                ProductIngredient.bom_version != active_bom_version,
                ProductIngredient.is_active_bom.is_(True),
            )
            .values(is_active_bom=False)
        )
        result = db.execute(stmt)
        return result.rowcount
