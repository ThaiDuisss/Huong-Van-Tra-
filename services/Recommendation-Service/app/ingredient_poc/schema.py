"""PoC-only SQLAlchemy Core metadata; importing this module performs no I/O.

This metadata deliberately does not use app.database.base.Base. IDs in foreign
keys belong to this dataset; source_* IDs are uninterpreted upstream references.
"""
from uuid import uuid4

from sqlalchemy import (
    CheckConstraint, Column, DateTime, ForeignKeyConstraint, Index, Integer, JSON,
    MetaData, Numeric, String, Table, UniqueConstraint, text,
)
from sqlalchemy.dialects.mysql import DATETIME
from sqlalchemy.ext.compiler import compiles
from sqlalchemy.sql.functions import FunctionElement

metadata = MetaData()
TIME = DateTime().with_variant(DATETIME(fsp=6), "mysql")


class ResearchTimestamp(FunctionElement):
    """Match MySQL DATETIME(6), while keeping SQLite memory tests portable."""
    type = TIME
    inherit_cache = True


@compiles(ResearchTimestamp)
def _timestamp_default(element, compiler, **kwargs):
    return "CURRENT_TIMESTAMP"


@compiles(ResearchTimestamp, "mysql")
def _mysql_timestamp_default(element, compiler, **kwargs):
    return "CURRENT_TIMESTAMP(6)"


QTY = Numeric(24, 8)
FACTOR = Numeric(18, 8)
KINDS = "'SYNTHETIC','STANDARD_CURRENT_PROXY','ACTUAL'"
STATES = "'STAGED','VALIDATED','QUARANTINED'"


def col(name, kind, *, nullable=False, **kwargs):
    return Column(name, kind, nullable=nullable, **kwargs)


def check(code, expression):
    return CheckConstraint(expression, name="ck_ip_" + code)


def unique(code, *names):
    return UniqueConstraint(*names, name="uq_ip_" + code)


def fk(code, names, target, target_names):
    return ForeignKeyConstraint(
        names, ["ingredient_poc_" + target + "." + n for n in target_names],
        # InnoDB defaults to RESTRICT/NO ACTION. Explicit referential actions
        # on CHECK columns are restricted by MySQL.
        name="fk_ip_" + code,
    )


def table(short, *parts):
    result = Table(
        "ingredient_poc_" + short, metadata,
        col("id", String(36), primary_key=True, default=lambda: str(uuid4())),
        *parts,
        mysql_engine="InnoDB", mysql_charset="utf8mb4", mysql_collate="utf8mb4_bin",
    )
    return result


def child(short, *parts):
    result = table(
        short, col("dataset_id", String(36)), col("source_kind", String(24)),
        col("created_at", TIME, server_default=ResearchTimestamp()),
        *parts,
        unique(short + "_tenant", "id", "dataset_id"),
        fk(short + "_dataset", ["dataset_id", "source_kind"],
           "datasets", ["id", "source_kind"]),
    )
    Index("ix_ip_" + short + "_dataset", result.c.dataset_id)
    return result


datasets = table(
    "datasets",
    col("dataset_name", String(128)), col("schema_version", Integer),
    col("source_kind", String(24)), col("source_system", String(64)),
    col("source_snapshot_hash", String(64)), col("status", String(16)),
    col("created_at", TIME, server_default=ResearchTimestamp()),
    col("validation_policy_version", String(64)), col("manifest_json", JSON),
    unique("datasets_kind", "id", "source_kind"),
    unique("datasets_identity", "dataset_name", "schema_version", "source_snapshot_hash"),
    check("datasets_kind", "source_kind IN (" + KINDS + ")"),
    check("datasets_state", "status IN ('STAGED','VALIDATED','PUBLISHED','REJECTED')"),
    check("datasets_version", "schema_version > 0"),
    check("datasets_hash", "length(source_snapshot_hash) = 64"),
    check("datasets_synthetic", "source_kind <> 'SYNTHETIC' OR source_system LIKE 'research:%'"),
)

customers = child(
    "customers", col("customer_id", String(36)), col("source_system", String(64)),
    unique("customers_identity", "dataset_id", "source_system", "customer_id"),
)


def source_reference(short):
    """Synthetic dimensions have local IDs, never invented upstream UUIDs."""
    return (
        col("source_system", String(64)),
        col("source_" + short + "_id", String(36), nullable=True),
        check(short + "_source", "(source_kind = 'SYNTHETIC' AND source_" + short
              + "_id IS NULL AND source_system LIKE 'research:%') OR "
              "(source_kind <> 'SYNTHETIC' AND source_" + short + "_id IS NOT NULL)"),
    )


units = child(
    "units", *source_reference("unit"), col("symbol", String(32)),
    col("dimension", String(8)),
    col("canonical_unit", String(8), nullable=True),
    col("conversion_factor", FACTOR, nullable=True),
    col("conversion_rule_version", String(64)), col("provenance", JSON),
    col("validation_status", String(16)),
    unique("units_symbol", "dataset_id", "symbol"),
    unique("units_snapshot", "id", "dataset_id", "symbol"),
    unique("units_conversion", "id", "dataset_id", "canonical_unit", "conversion_factor"),
    check("units_dimension", "dimension IN ('MASS','VOLUME','COUNT')"),
    check("units_state", "validation_status IN (" + STATES + ")"),
    check("units_conversion",
          "(canonical_unit IS NULL AND conversion_factor IS NULL) OR "
          "(canonical_unit IS NOT NULL AND conversion_factor IS NOT NULL AND "
          "conversion_factor > 0 AND ((dimension = 'MASS' AND canonical_unit = 'g') OR "
          "(dimension = 'VOLUME' AND canonical_unit = 'ml') OR "
          "(dimension = 'COUNT' AND canonical_unit = 'count' AND conversion_factor = 1)))"),
    check("units_validated", "validation_status <> 'VALIDATED' OR canonical_unit IS NOT NULL"),
    check("units_physical_factor", "canonical_unit IS NULL OR dimension = 'COUNT' OR "
          "(dimension = 'MASS' AND ((symbol = 'g' AND conversion_factor = 1) OR "
          "(symbol = 'kg' AND conversion_factor = 1000))) OR "
          "(dimension = 'VOLUME' AND ((symbol = 'ml' AND conversion_factor = 1) OR "
          "(symbol IN ('l','L') AND conversion_factor = 1000)))"),
)

ingredients = child(
    "ingredients", *source_reference("ingredient"),
    col("ingredient_code", String(100)), col("name_snapshot", String(255)),
    col("provenance", JSON),
    unique("ingredients_code", "dataset_id", "source_system", "ingredient_code"),
    unique("ingredients_source", "dataset_id", "source_system", "source_ingredient_id"),
    unique("ingredients_snapshot", "id", "dataset_id", "ingredient_code"),
)

products = child(
    "products", *source_reference("product"),
    col("product_code", String(100)), col("name_snapshot", String(255)),
    col("provenance", JSON),
    unique("products_code", "dataset_id", "source_system", "product_code"),
    unique("products_source", "dataset_id", "source_system", "source_product_id"),
)

variants = child(
    "variants", *source_reference("variant"), col("product_id", String(36)),
    col("sku", String(100)), col("name_snapshot", String(255)),
    col("sale_unit_id", String(36)), col("provenance", JSON),
    unique("variants_sku", "dataset_id", "source_system", "sku"),
    unique("variants_source", "dataset_id", "source_system", "source_variant_id"),
    unique("variants_product", "id", "product_id", "dataset_id", "sale_unit_id"),
    fk("variants_product", ["product_id", "dataset_id"], "products", ["id", "dataset_id"]),
    fk("variants_unit", ["sale_unit_id", "dataset_id"], "units", ["id", "dataset_id"]),
)

orders = child(
    "orders", col("source_system", String(64)), col("source_order_id", String(36)),
    col("customer_id", String(36)), col("order_status", String(16)),
    col("payment_status", String(16)),
    unique("orders_business", "dataset_id", "source_system", "source_order_id"),
    unique("orders_customer", "id", "customer_id", "dataset_id", "order_status", "payment_status"),
    fk("orders_customer", ["customer_id", "dataset_id"], "customers", ["id", "dataset_id"]),
    check("orders_completed", "order_status = 'COMPLETED' AND payment_status = 'PAID'"),
)

recipes = child(
    "recipes", col("recipe_family_key", String(128)), col("recipe_version", Integer),
    col("recipe_kind", String(24)),
    col("base_product_id", String(36), nullable=True),
    col("base_variant_id", String(36), nullable=True),
    col("base_sale_unit_id", String(36), nullable=True),
    col("portion_quantity", QTY, nullable=True),
    col("portion_unit_id", String(36), nullable=True),
    col("portion_unit", String(32), nullable=True),
    col("servings_per_recipe", QTY, nullable=True),
    col("recipe_hash", String(64)), col("provenance", JSON),
    col("validation_status", String(16)),
    unique("recipes_version", "dataset_id", "recipe_family_key", "recipe_version"),
    unique("recipes_kind", "id", "dataset_id", "recipe_kind"),
    fk("recipes_base", ["base_variant_id", "base_product_id", "dataset_id", "base_sale_unit_id"],
       "variants", ["id", "product_id", "dataset_id", "sale_unit_id"]),
    fk("recipes_portion", ["portion_unit_id", "dataset_id", "portion_unit"],
       "units", ["id", "dataset_id", "symbol"]),
    check("recipes_kind", "recipe_kind IN ('STANDARD_CURRENT_PROXY','CUSTOM')"),
    check("recipes_origin", "source_kind <> 'STANDARD_CURRENT_PROXY' OR recipe_kind = 'STANDARD_CURRENT_PROXY'"),
    check("recipes_version", "recipe_version > 0"),
    check("recipes_hash", "length(recipe_hash) = 64"),
    check("recipes_state", "validation_status IN (" + STATES + ")"),
    check("recipes_base", "(base_product_id IS NULL AND base_variant_id IS NULL AND base_sale_unit_id IS NULL) OR "
          "(base_product_id IS NOT NULL AND base_variant_id IS NOT NULL AND base_sale_unit_id IS NOT NULL)"),
    check("recipes_standard", "recipe_kind <> 'STANDARD_CURRENT_PROXY' OR base_variant_id IS NOT NULL"),
    check("recipes_portion", "(portion_quantity IS NULL AND portion_unit_id IS NULL AND portion_unit IS NULL) OR "
          "(portion_quantity IS NOT NULL AND portion_quantity > 0 AND portion_unit_id IS NOT NULL AND portion_unit IS NOT NULL)"),
    check("recipes_servings", "servings_per_recipe IS NULL OR servings_per_recipe > 0"),
)
Index("ix_ip_recipes_family", recipes.c.dataset_id, recipes.c.recipe_family_key)
Index("ix_ip_recipes_hash", recipes.c.dataset_id, recipes.c.recipe_hash)

recipe_items = child(
    "recipe_items", col("recipe_id", String(36)), col("ingredient_id", String(36)),
    col("ingredient_code_snapshot", String(100)),
    col("ingredient_role", String(32), nullable=True),
    col("quantity_original", QTY), col("unit_id", String(36)),
    col("unit_symbol_snapshot", String(32)),
    col("quantity_canonical", QTY, nullable=True),
    col("canonical_unit", String(8), nullable=True),
    col("canonical_factor", FACTOR, nullable=True),
    col("waste_rate", Numeric(9, 4), nullable=True),
    col("validation_status", String(16)),
    unique("recipe_items_ingredient", "dataset_id", "recipe_id", "ingredient_id"),
    fk("recipe_items_recipe", ["recipe_id", "dataset_id"], "recipes", ["id", "dataset_id"]),
    fk("recipe_items_ingredient", ["ingredient_id", "dataset_id", "ingredient_code_snapshot"],
       "ingredients", ["id", "dataset_id", "ingredient_code"]),
    fk("recipe_items_unit", ["unit_id", "dataset_id", "unit_symbol_snapshot"],
       "units", ["id", "dataset_id", "symbol"]),
    fk("recipe_items_conversion", ["unit_id", "dataset_id", "canonical_unit", "canonical_factor"],
       "units", ["id", "dataset_id", "canonical_unit", "conversion_factor"]),
    check("recipe_items_quantity", "quantity_original > 0"),
    check("recipe_items_state", "validation_status IN (" + STATES + ")"),
    check("recipe_items_canonical",
          "(quantity_canonical IS NULL AND canonical_unit IS NULL AND canonical_factor IS NULL) OR "
          "(quantity_canonical IS NOT NULL AND canonical_unit IS NOT NULL AND canonical_factor IS NOT NULL AND "
          "canonical_factor > 0 AND quantity_canonical > 0 AND "
          "quantity_canonical = ROUND(quantity_original * canonical_factor, 8))"),
    check("recipe_items_validated", "validation_status <> 'VALIDATED' OR quantity_canonical IS NOT NULL"),
    check("recipe_items_waste", "waste_rate IS NULL OR waste_rate >= 0"),
)
Index("ix_ip_recipe_items_ingredient", recipe_items.c.dataset_id, recipe_items.c.ingredient_id)

recipe_snapshots = child(
    "recipe_snapshots", col("recipe_id", String(36)),
    col("recipe_kind", String(24), server_default=text("'CUSTOM'")), col("order_id", String(36)),
    col("order_item_id", String(36)), col("source_system", String(64)),
    col("source_snapshot_id", String(128)), col("observed_at", TIME),
    col("payload_hash", String(64)), col("provenance", JSON),
    unique("snapshots_source", "dataset_id", "source_system", "source_snapshot_id"),
    unique("snapshots_line", "dataset_id", "order_id", "order_item_id"),
    unique("snapshots_reference", "id", "recipe_id", "order_id", "order_item_id", "dataset_id"),
    fk("snapshots_recipe", ["recipe_id", "dataset_id", "recipe_kind"],
       "recipes", ["id", "dataset_id", "recipe_kind"]),
    fk("snapshots_order", ["order_id", "dataset_id"], "orders", ["id", "dataset_id"]),
    check("snapshots_actual", "source_kind = 'ACTUAL'"),
    check("snapshots_recipe", "recipe_kind = 'CUSTOM'"),
    check("snapshots_hash", "length(payload_hash) = 64"),
)

sale_items = child(
    "sale_items", col("customer_id", String(36)), col("order_id", String(36)),
    col("order_item_id", String(36)), col("purchase_type", String(16)),
    col("product_id", String(36), nullable=True), col("variant_id", String(36), nullable=True),
    col("recipe_id", String(36), nullable=True), col("recipe_kind", String(24), nullable=True),
    col("actual_recipe_snapshot_id", String(36), nullable=True),
    col("sale_unit_id", String(36)), col("quantity", QTY), col("purchased_at", TIME),
    col("order_status", String(16)), col("payment_status", String(16)),
    col("source_system", String(64)), col("source_event_id", String(36), nullable=True),
    col("source_revision", Integer), col("payload_hash", String(64)),
    unique("sales_business", "dataset_id", "order_id", "order_item_id"),
    unique("sales_event_line", "dataset_id", "source_system", "source_event_id", "order_item_id"),
    fk("sales_order", ["order_id", "customer_id", "dataset_id", "order_status", "payment_status"],
       "orders", ["id", "customer_id", "dataset_id", "order_status", "payment_status"]),
    fk("sales_variant", ["variant_id", "product_id", "dataset_id", "sale_unit_id"],
       "variants", ["id", "product_id", "dataset_id", "sale_unit_id"]),
    fk("sales_recipe", ["recipe_id", "dataset_id", "recipe_kind"],
       "recipes", ["id", "dataset_id", "recipe_kind"]),
    fk("sales_snapshot", ["actual_recipe_snapshot_id", "recipe_id", "order_id", "order_item_id", "dataset_id"],
       "recipe_snapshots", ["id", "recipe_id", "order_id", "order_item_id", "dataset_id"]),
    fk("sales_unit", ["sale_unit_id", "dataset_id"], "units", ["id", "dataset_id"]),
    check("sales_type", "(purchase_type = 'PRODUCT' AND product_id IS NOT NULL AND variant_id IS NOT NULL) OR "
          "(purchase_type = 'CUSTOM_BOM' AND product_id IS NULL AND variant_id IS NULL AND recipe_id IS NOT NULL AND recipe_kind = 'CUSTOM')"),
    check("sales_recipe_pair", "(recipe_id IS NULL AND recipe_kind IS NULL) OR (recipe_id IS NOT NULL AND recipe_kind IS NOT NULL)"),
    check("sales_actual", "(actual_recipe_snapshot_id IS NULL OR (source_kind = 'ACTUAL' AND recipe_id IS NOT NULL AND recipe_kind = 'CUSTOM')) AND "
          "(source_kind <> 'ACTUAL' OR recipe_kind IS NULL OR recipe_kind <> 'CUSTOM' OR actual_recipe_snapshot_id IS NOT NULL)"),
    check("sales_proxy", "source_kind <> 'STANDARD_CURRENT_PROXY' OR purchase_type = 'PRODUCT'"),
    check("sales_quantity", "quantity > 0"),
    check("sales_revision", "source_revision > 0"),
    check("sales_hash", "length(payload_hash) = 64"),
)
Index("ix_ip_sales_customer_time", sale_items.c.dataset_id, sale_items.c.customer_id, sale_items.c.purchased_at)
Index("ix_ip_sales_recipe_time", sale_items.c.dataset_id, sale_items.c.recipe_id, sale_items.c.purchased_at)

feedback = child(
    "feedback", col("sale_item_id", String(36)), col("source_system", String(64)),
    col("source_feedback_id", String(128)), col("feedback_type", String(24)),
    col("rating", Integer, nullable=True), col("return_reason", String(64), nullable=True),
    col("feedback_time", TIME),
    unique("feedback_source", "dataset_id", "source_system", "source_feedback_id"),
    fk("feedback_sale", ["sale_item_id", "dataset_id"], "sale_items", ["id", "dataset_id"]),
    check("feedback_type", "feedback_type IN ('RATING','PREFERENCE_DISLIKE','OPERATIONAL_RETURN')"),
    check("feedback_rating", "(feedback_type = 'RATING' AND rating IS NOT NULL AND rating BETWEEN 1 AND 5) OR "
          "(feedback_type <> 'RATING' AND rating IS NULL)"),
    check("feedback_reason", "(feedback_type <> 'OPERATIONAL_RETURN' OR "
          "(return_reason IS NOT NULL AND return_reason IN ('DEFECTIVE','DAMAGED','WRONG_ITEM','QUALITY_ISSUE','EXPIRED','OTHER'))) AND "
          "(feedback_type <> 'PREFERENCE_DISLIKE' OR return_reason IS NULL OR return_reason = 'CHANGE_OF_MIND')"),
)
Index("ix_ip_feedback_time", feedback.c.dataset_id, feedback.c.feedback_time)
