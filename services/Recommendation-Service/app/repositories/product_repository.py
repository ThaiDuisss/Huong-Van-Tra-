from sqlalchemy.dialects.mysql import insert
from sqlalchemy.orm import Session

from app.models.product import Product


class ProductRepository:
    def upsert(
        self,
        db: Session,
        product: Product,
    ) -> None:
        stmt = insert(Product).values(
            id=product.id,
            category_id=product.category_id,
            product_code=product.product_code,
            name=product.name,
            status=product.status,
            is_deleted=product.is_deleted,
        )

        stmt = stmt.on_duplicate_key_update(
            category_id=stmt.inserted.category_id,
            product_code=stmt.inserted.product_code,
            name=stmt.inserted.name,
            status=stmt.inserted.status,
            is_deleted=stmt.inserted.is_deleted,
        )

        db.execute(stmt)

    def find_by_id(
        self,
        db: Session,
        product_id: str,
    ) -> Product | None:
        return db.get(Product, product_id)
