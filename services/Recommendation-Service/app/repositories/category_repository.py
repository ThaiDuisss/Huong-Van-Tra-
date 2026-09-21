from sqlalchemy.dialects.mysql import insert
from sqlalchemy.orm import Session

from app.models.category import Category


class CategoryRepository:

    def upsert(
        self,
        db: Session,
        category: Category,
    ) -> None:
        stmt = insert(Category).values(
            id=category.id,
            category_code=category.category_code,
            name=category.name,
            parent_id=category.parent_id,
            status=category.status,
            is_deleted=category.is_deleted,
        )

        stmt = stmt.on_duplicate_key_update(
            category_code=stmt.inserted.category_code,
            name=stmt.inserted.name,
            parent_id=stmt.inserted.parent_id,
            status=stmt.inserted.status,
            is_deleted=stmt.inserted.is_deleted,
        )

        db.execute(stmt)