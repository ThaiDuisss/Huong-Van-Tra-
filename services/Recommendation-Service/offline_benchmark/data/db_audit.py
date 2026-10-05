"""Read-only row counts for benchmark connectivity and before/after verification."""
from pathlib import Path

from sqlalchemy import create_engine, func, select

from app.models.category import Category
from app.models.customer_interaction import CustomerInteraction
from app.models.ingredient import Ingredient
from app.models.interaction_event import InteractionEvent
from app.models.product import Product
from app.models.product_ingredient import ProductIngredient
from app.models.product_variant import ProductVariant


TABLES = (InteractionEvent, CustomerInteraction, Product, ProductVariant,
          ProductIngredient, Ingredient, Category)


def read_table_counts(env_file: Path | None = None) -> dict[str, int]:
    from app.core.settings import Settings

    if env_file is not None and not env_file.is_file():
        raise ValueError("The selected existing application environment file is missing")
    settings = Settings(_env_file=env_file) if env_file is not None else Settings()
    engine = create_engine(settings.sqlalchemy_database_url, echo=False,
                           connect_args={"connect_timeout": 10})
    try:
        with engine.connect() as connection:
            connection.exec_driver_sql("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            connection.exec_driver_sql("SET TRANSACTION READ ONLY")
            connection.exec_driver_sql("START TRANSACTION WITH CONSISTENT SNAPSHOT")
            try:
                return {model.__tablename__: int(connection.scalar(select(func.count()).select_from(model)))
                        for model in TABLES}
            finally:
                connection.rollback()
    finally:
        engine.dispose()
