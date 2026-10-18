from collections import defaultdict
from dataclasses import dataclass

from sqlalchemy import case, func, select
from sqlalchemy.orm import Session

from app.models.category import Category
from app.models.customer_interaction import CustomerInteraction
from app.models.ingredient import Ingredient
from app.models.interaction_event import InteractionEvent, InteractionEventType
from app.models.product import Product
from app.models.product_ingredient import ProductIngredient
from app.recommendation.v1_model import CatalogProduct, PurchaseCount


@dataclass(frozen=True, slots=True)
class CustomerPurchaseHistory:
    distinct_order_count: int
    product_counts: dict[str, int]
    missing_order_events: int = 0


class V1RecommendationRepository:
    """SELECT-only serving projections; entire history is read only at build time."""

    @staticmethod
    def _valid_order_id():
        return (InteractionEvent.order_id.is_not(None)
                & (func.trim(InteractionEvent.order_id) != ""))

    def count_distinct_purchase_orders(self, db: Session, customer_id: str) -> tuple[int, int]:
        valid = self._valid_order_id()
        row = db.execute(select(
            func.count(func.distinct(case((valid, InteractionEvent.order_id)))),
            func.coalesce(func.sum(case((valid, 0), else_=1)), 0),
        ).where(InteractionEvent.customer_id == customer_id,
                InteractionEvent.event_type == InteractionEventType.PURCHASE.value)).one()
        return int(row[0]), int(row[1])

    def customer_history(self, db: Session, customer_id: str) -> CustomerPurchaseHistory:
        order_count, missing = self.count_distinct_purchase_orders(db, customer_id)
        rows = db.execute(select(InteractionEvent.product_id, func.count()).where(
            InteractionEvent.customer_id == customer_id,
            InteractionEvent.event_type == InteractionEventType.PURCHASE.value,
        ).group_by(InteractionEvent.product_id))
        # Missing-order PURCHASEs retain product preference/exclusion semantics,
        # but never count as invented distinct orders for routing.
        return CustomerPurchaseHistory(order_count, {p: int(n) for p, n in rows}, missing)

    def active_catalog(self, db: Session) -> tuple[CatalogProduct, ...]:
        products = list(db.execute(select(
            Product.id, Product.product_code, Product.name, Category.category_code,
        ).outerjoin(Category, Category.id == Product.category_id).where(
            Product.status == "ACTIVE", Product.is_deleted.is_(False),
        ).order_by(Product.id)))
        rows = db.execute(select(ProductIngredient.product_id, Ingredient.ingredient_code).join(
            Ingredient, Ingredient.id == ProductIngredient.ingredient_id,
        ).join(Product, Product.id == ProductIngredient.product_id).where(
            Product.status == "ACTIVE", Product.is_deleted.is_(False),
            ProductIngredient.is_active_bom.is_(True), Ingredient.type == 1,
        ))
        ingredients = defaultdict(set)
        for product_id, code in rows:
            if code and code.strip():
                ingredients[product_id].add(code.strip())
        return tuple(CatalogProduct(p, code, name, category,
                                    tuple(sorted(ingredients[p]))) for p, code, name, category in products)

    def popularity(self, db: Session, purchased: set[str], limit: int,
                   eligible_ids: set[str] | None = None):
        popularity = func.coalesce(func.sum(CustomerInteraction.total_purchase_count), 0).label("popularity")
        statement = select(Product.id, Product.product_code, Product.name, popularity).outerjoin(
            CustomerInteraction, CustomerInteraction.product_id == Product.id,
        ).where(Product.status == "ACTIVE", Product.is_deleted.is_(False))
        if purchased:
            statement = statement.where(Product.id.not_in(purchased))
        if eligible_ids is not None:
            statement = statement.where(Product.id.in_(eligible_ids))
        return list(db.execute(statement.group_by(Product.id, Product.product_code, Product.name)
                               .order_by(popularity.desc(), Product.id.asc()).limit(limit)))

    def build_purchase_counts(self, db: Session) -> tuple[PurchaseCount, ...]:
        rows = db.execute(select(InteractionEvent.customer_id, InteractionEvent.product_id,
                                 func.count()).where(
            InteractionEvent.event_type == InteractionEventType.PURCHASE.value,
            InteractionEvent.customer_id.is_not(None), func.trim(InteractionEvent.customer_id) != "",
        ).group_by(InteractionEvent.customer_id, InteractionEvent.product_id)
          .order_by(InteractionEvent.customer_id, InteractionEvent.product_id))
        return tuple(PurchaseCount(u, p, int(n)) for u, p, n in rows)

    def missing_purchase_order_events(self, db: Session) -> int:
        return int(db.scalar(select(func.count()).select_from(InteractionEvent).where(
            InteractionEvent.event_type == InteractionEventType.PURCHASE.value,
            ~self._valid_order_id(),
        )) or 0)
