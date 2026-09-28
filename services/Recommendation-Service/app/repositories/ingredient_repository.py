from sqlalchemy.dialects.mysql import insert
from sqlalchemy.orm import Session

from app.models.ingredient import Ingredient


class IngredientRepository:
    def upsert(
        self,
        db: Session,
        ingredient: Ingredient,
    ) -> None:
        stmt = insert(Ingredient).values(
            id=ingredient.id,
            ingredient_code=ingredient.ingredient_code,
            name=ingredient.name,
            status=ingredient.status,
            type=ingredient.type,
            is_deleted=ingredient.is_deleted,
        )

        stmt = stmt.on_duplicate_key_update(
            ingredient_code=stmt.inserted.ingredient_code,
            name=stmt.inserted.name,
            status=stmt.inserted.status,
            type=stmt.inserted.type,
            is_deleted=stmt.inserted.is_deleted,
        )

        db.execute(stmt)

    def find_by_id(
        self,
        db: Session,
        ingredient_id: str,
    ) -> Ingredient | None:
        return db.get(Ingredient, ingredient_id)
