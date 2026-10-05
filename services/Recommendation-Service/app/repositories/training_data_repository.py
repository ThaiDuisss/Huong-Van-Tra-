from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app.models.category import Category
from app.models.ingredient import Ingredient
from app.models.interaction_event import InteractionEvent
from app.models.product import Product
from app.models.product_ingredient import ProductIngredient
from app.training.schemas import (
    ActiveProductIngredientRecord,
    ActiveProductRecord,
    TrainingDataSnapshot,
    TrainingInteractionEvent,
)


ACTIVE_PRODUCT_STATUS = "ACTIVE"
# ingredients.type = 0 marks packaging / non-recipe material (nylon bag, box, label...).
# Such rows are excluded from item features because they carry no taste signal.
PACKAGING_INGREDIENT_TYPE = 0


class TrainingDataRepository:
    """Loads a deterministic, read-only snapshot from recommendation_db."""

    def load_snapshot(self, db: Session) -> TrainingDataSnapshot:
        with db.no_autoflush:
            raw_event_count = self._count_interaction_events(db)
            interaction_events = self._load_interaction_events(db)
            active_products = self._load_active_products(db)
            active_product_ingredients = self._load_active_product_ingredients(db)

        return TrainingDataSnapshot(
            raw_event_count=raw_event_count,
            interaction_events=interaction_events,
            active_products=active_products,
            active_product_ingredients=active_product_ingredients,
        )

    @staticmethod
    def _count_interaction_events(db: Session) -> int:
        stmt = select(func.count(InteractionEvent.id))
        return int(db.scalar(stmt) or 0)

    @staticmethod
    def _load_interaction_events(
        db: Session,
    ) -> tuple[TrainingInteractionEvent, ...]:
        stmt = (
            select(
                InteractionEvent.customer_id,
                InteractionEvent.product_id,
                InteractionEvent.event_type,
                InteractionEvent.return_reason,
            )
            .order_by(
                InteractionEvent.customer_id.asc(),
                InteractionEvent.product_id.asc(),
                InteractionEvent.event_type.asc(),
                InteractionEvent.return_reason.asc(),
                InteractionEvent.event_time.asc(),
                InteractionEvent.id.asc(),
            )
        )
        return tuple(
            TrainingInteractionEvent(
                customer_id=row.customer_id,
                product_id=row.product_id,
                event_type=row.event_type,
                return_reason=row.return_reason,
            )
            for row in db.execute(stmt)
        )

    @staticmethod
    def load_active_products(
        db: Session,
    ) -> tuple[ActiveProductRecord, ...]:
        """Reusable read-only catalog projection for training/offline consumers."""
        return TrainingDataRepository._load_active_products(db)

    @staticmethod
    def _load_active_products(
        db: Session,
    ) -> tuple[ActiveProductRecord, ...]:
        stmt = (
            select(Product.id, Category.category_code)
            .outerjoin(Category, Category.id == Product.category_id)
            .where(
                Product.status == ACTIVE_PRODUCT_STATUS,
                Product.is_deleted.is_(False),
            )
            .order_by(Product.id.asc())
        )
        return tuple(
            ActiveProductRecord(
                product_id=row.id,
                category_code=row.category_code,
            )
            for row in db.execute(stmt)
        )

    @staticmethod
    def _load_active_product_ingredients(
        db: Session,
    ) -> tuple[ActiveProductIngredientRecord, ...]:
        stmt = (
            select(ProductIngredient.product_id, Ingredient.ingredient_code)
            .join(Product, Product.id == ProductIngredient.product_id)
            .join(Ingredient, Ingredient.id == ProductIngredient.ingredient_id)
            .where(
                Product.status == ACTIVE_PRODUCT_STATUS,
                Product.is_deleted.is_(False),
                ProductIngredient.is_active_bom.is_(True),
                or_(
                    Ingredient.type.is_(None),
                    Ingredient.type != PACKAGING_INGREDIENT_TYPE,
                ),
            )
            .order_by(
                ProductIngredient.product_id.asc(),
                Ingredient.ingredient_code.asc(),
                ProductIngredient.source_variant_id.asc(),
                ProductIngredient.id.asc(),
            )
        )
        return tuple(
            ActiveProductIngredientRecord(
                product_id=row.product_id,
                ingredient_code=row.ingredient_code,
            )
            for row in db.execute(stmt)
        )
