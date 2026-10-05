from collections import defaultdict
from pathlib import Path

from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import Session

from app.models.ingredient import Ingredient
from app.models.interaction_event import InteractionEvent, InteractionEventType
from app.models.product_ingredient import ProductIngredient
from app.repositories.training_data_repository import TrainingDataRepository
from offline_benchmark.common.types import CatalogProduct, DatabaseSnapshot, PurchaseEvent


def relevant_ingredients(rows) -> dict[str, tuple[str, ...]]:
    """Keep recipe ingredients explicitly marked type=1, deduplicated across variants."""
    grouped: dict[str, set[str]] = defaultdict(set)
    for product_id, ingredient_code, ingredient_type in rows:
        if ingredient_type == 1 and ingredient_code and ingredient_code.strip():
            grouped[product_id].add(ingredient_code.strip())
    return {product: tuple(sorted(codes)) for product, codes in grouped.items()}


def load_snapshot(env_file: Path | None = None) -> DatabaseSnapshot:
    # Loading Settings here avoids configuration requirements for pure model/tests.
    # The application honors .env/process environment; an explicit file uses that
    # same Settings mechanism, without guessing alternate credentials on failure.
    from app.core.settings import Settings

    if env_file is not None and not env_file.is_file():
        raise ValueError("The selected existing application environment file is missing")
    settings = Settings(_env_file=env_file) if env_file is not None else Settings()
    engine = create_engine(
        settings.sqlalchemy_database_url,
        echo=False,
        connect_args={"connect_timeout": 10},
    )
    try:
        with engine.connect() as connection:
            # SQLAlchemy's transaction tracks the explicit MySQL transaction too.
            # All metadata/history reads share one snapshot. No ORM flush/commit.
            connection.exec_driver_sql("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            connection.exec_driver_sql("SET TRANSACTION READ ONLY")
            connection.exec_driver_sql("START TRANSACTION WITH CONSISTENT SNAPSHOT")
            try:
                with Session(bind=connection, autoflush=False) as db:
                    return read_snapshot(db)
            finally:
                connection.rollback()
    finally:
        engine.dispose()


def read_snapshot(db: Session) -> DatabaseSnapshot:
    stmt = (
        select(
            InteractionEvent.id,
            InteractionEvent.customer_id,
            InteractionEvent.product_id,
            InteractionEvent.event_time,
            InteractionEvent.order_id,
            InteractionEvent.event_type,
        )
        .where(
            InteractionEvent.event_type == InteractionEventType.PURCHASE.value,
            InteractionEvent.customer_id.is_not(None),
            func.trim(InteractionEvent.customer_id) != "",
        )
        .order_by(InteractionEvent.customer_id, InteractionEvent.event_time, InteractionEvent.id)
    )
    purchases = tuple(PurchaseEvent(*row) for row in db.execute(stmt))
    # Reuse production's active/nondeleted product and category projection.
    products = TrainingDataRepository.load_active_products(db)
    ingredient_rows = db.execute(
        select(ProductIngredient.product_id, Ingredient.ingredient_code, Ingredient.type)
        .join(Ingredient, Ingredient.id == ProductIngredient.ingredient_id)
        .where(ProductIngredient.is_active_bom.is_(True), Ingredient.type == 1)
    )
    ingredients = relevant_ingredients(ingredient_rows)
    return DatabaseSnapshot(
        purchases,
        tuple(
            CatalogProduct(product.product_id, product.category_code,
                           ingredients.get(product.product_id, ()))
            for product in products
        ),
    )
