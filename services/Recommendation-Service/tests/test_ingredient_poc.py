"""Research contracts on SQLite memory only; never load env/service/session."""
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
import importlib.util
import re
import sys
import unittest
from uuid import uuid4

from pydantic import ValidationError
from sqlalchemy import create_engine, event, insert, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from sqlalchemy.schema import CreateTable

from app.ingredient_poc import schema as s
from app.ingredient_poc.contracts import (
    RecipeContract, RecipeItemContract, SaleContract, UnitContract, recipe_hash,
)
from app.ingredient_poc.ddl import mysql_migration
from app.ingredient_poc.repository import ImportConflict, PocRepository


def identity():
    return str(uuid4())


GRAM = UnitContract(symbol="g", dimension="MASS", canonical_unit="g",
                    conversion_factor=Decimal(1), conversion_rule_version="mass-v1")


def item(code="TEA", quantity="20", **kwargs):
    return RecipeItemContract(
        ingredient_namespace="research:fixture", ingredient_code=code,
        quantity_original=Decimal(quantity), unit=GRAM,
        quantity_canonical=Decimal(quantity), **kwargs,
    )


def recipe(**kwargs):
    values = dict(recipe_family_key="oolong", recipe_version=1, recipe_kind="CUSTOM",
                  effective_time_unknown=True, items=(item(), item("SUGAR", "15")))
    values.update(kwargs)
    return RecipeContract(**values)


class ContractTests(unittest.TestCase):
    def test_hash_preserves_quantity_and_ignores_version_label(self):
        base = recipe()
        self.assertEqual(recipe_hash(base), recipe_hash(base.model_copy(update={"recipe_version": 2})))
        self.assertNotEqual(recipe_hash(recipe(items=(item(quantity="10"),))), recipe_hash(recipe(items=(item(quantity="20"),))))

    def test_hash_is_order_and_decimal_format_independent(self):
        self.assertEqual(recipe_hash(recipe()),
                         recipe_hash(recipe(items=(item("SUGAR", "15.00"), item("TEA", "20.000")))))

    def test_same_family_can_have_different_recipe_versions(self):
        older = recipe(items=(item(quantity="10"),))
        newer = recipe(recipe_version=2, items=(item(quantity="20"),))
        self.assertEqual(older.recipe_family_key, newer.recipe_family_key)
        self.assertNotEqual(recipe_hash(older), recipe_hash(newer))

    def test_no_mass_volume_or_ambiguous_pack_conversion(self):
        with self.assertRaises(ValidationError):
            UnitContract(symbol="g", dimension="MASS", canonical_unit="ml",
                         conversion_factor=1, conversion_rule_version="bad")
        with self.assertRaises(ValidationError):
            UnitContract(symbol="box24", dimension="COUNT", canonical_unit="count",
                         conversion_factor=24, conversion_rule_version="unverified")
        with self.assertRaises(ValidationError):
            UnitContract(symbol="kg", dimension="MASS", canonical_unit="g",
                         conversion_factor=1, conversion_rule_version="wrong-scale")

    def test_quantity_validation_excludes_waste(self):
        self.assertEqual(item(waste_rate=Decimal(5)).quantity_canonical, Decimal(20))
        with self.assertRaises(ValidationError):
            RecipeItemContract(ingredient_namespace="research:fixture", ingredient_code="TEA",
                               quantity_original=20, unit=GRAM, quantity_canonical=21, waste_rate=5)
        for q in ("0", "-1", "NaN", "Infinity"):
            with self.subTest(q=q), self.assertRaises(ValidationError):
                item(quantity=q)

    def test_recipe_requires_unique_ingredients_and_honest_proxy(self):
        with self.assertRaises(ValidationError):
            recipe(items=(item(), item()))
        with self.assertRaises(ValidationError):
            recipe(recipe_kind="STANDARD_CURRENT_PROXY", effective_time_unknown=False)
        with self.assertRaises(ValidationError):
            recipe(portion_quantity=1)


class SchemaTests(unittest.TestCase):
    def setUp(self):
        self.engine = create_engine("sqlite://", poolclass=StaticPool)

        @event.listens_for(self.engine, "connect")
        def enable_foreign_keys(connection, _):
            connection.execute("PRAGMA foreign_keys=ON")

        s.metadata.create_all(self.engine)  # Explicit test-only isolated metadata.
        self.db = self.engine.connect()
        self.tx = self.db.begin()
        self.dataset = self.add_dataset()
        self.customer = self.add("customers", customer_id=identity(), source_system="research:fixture")
        self.gram = self.add("units", source_system="research:fixture", source_unit_id=None,
                             symbol="g", dimension="MASS", canonical_unit="g", conversion_factor=1,
                             conversion_rule_version="mass-v1", provenance={},
                             validation_status="VALIDATED")
        self.count = self.add("units", source_system="research:fixture", source_unit_id=None,
                              symbol="pack", dimension="COUNT", canonical_unit="count", conversion_factor=1,
                              conversion_rule_version="count-v1", provenance={},
                              validation_status="VALIDATED")
        self.product = self.add("products", source_system="research:fixture",
                                source_product_id=None, product_code="P-TEA", name_snapshot="Tea",
                                provenance={})
        self.variant = self.add("variants", source_system="research:fixture", source_variant_id=None,
                                product_id=self.product, sku="P-TEA-BOX", name_snapshot="Tea box",
                                sale_unit_id=self.count, provenance={})
        self.order = self.add_order()
        self.recipe = self.add_recipe()

    def tearDown(self):
        self.tx.rollback()
        self.db.close()
        self.engine.dispose()

    def add_dataset(self, source_kind="SYNTHETIC"):
        key = identity()
        self.db.execute(insert(s.datasets).values(
            id=key, dataset_name="fixture-" + key, schema_version=1, source_kind=source_kind,
            source_system="research:fixture" if source_kind == "SYNTHETIC" else "order-service",
            source_snapshot_hash="a" * 64, status="STAGED",
            validation_policy_version="phase1a-v1", manifest_json={},
        ))
        return key

    def add(self, table, *, dataset=None, kind="SYNTHETIC", **values):
        key = values.pop("id", identity())
        self.db.execute(insert(s.metadata.tables["ingredient_poc_" + table]).values(
            id=key, dataset_id=dataset or self.dataset, source_kind=kind, **values))
        return key

    def add_order(self, **kwargs):
        values = dict(source_system="research:fixture", source_order_id=identity(),
                      customer_id=self.customer, order_status="COMPLETED", payment_status="PAID")
        values.update(kwargs)
        return self.add("orders", **values)

    def add_recipe(self):
        key = self.add("recipes", recipe_family_key="oolong", recipe_version=1,
                       recipe_kind="CUSTOM", recipe_hash=recipe_hash(recipe()), provenance={},
                       validation_status="VALIDATED")
        for code, qty in [("TEA", 20), ("SUGAR", 15)]:
            ingredient = self.add("ingredients", source_system="research:fixture",
                                  source_ingredient_id=None, ingredient_code=code,
                                  name_snapshot=code, provenance={})
            self.add("recipe_items", recipe_id=key, ingredient_id=ingredient,
                     ingredient_code_snapshot=code, quantity_original=qty, unit_id=self.gram,
                     unit_symbol_snapshot="g", quantity_canonical=qty, canonical_unit="g",
                     canonical_factor=1, waste_rate=5, validation_status="VALIDATED")
        return key

    def sale(self, purchase_type="PRODUCT", **kwargs):
        values = dict(dataset_id=self.dataset, customer_id=self.customer, order_id=self.order,
                      order_item_id=identity(), purchase_type=purchase_type,
                      product_id=self.product if purchase_type == "PRODUCT" else None,
                      variant_id=self.variant if purchase_type == "PRODUCT" else None,
                      recipe_id=self.recipe if purchase_type == "CUSTOM_BOM" else None,
                      recipe_kind="CUSTOM" if purchase_type == "CUSTOM_BOM" else None,
                      sale_unit_id=self.count, quantity=Decimal(1),
                      purchased_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
                      order_status="COMPLETED", payment_status="PAID", source_kind="SYNTHETIC",
                      source_system="research:fixture", source_revision=1)
        values.update(kwargs)
        return SaleContract(**values)

    def persist(self, contract):
        values = contract.model_dump(mode="python")
        for key, value in list(values.items()):
            if value is not None and key.endswith("_id"):
                values[key] = str(value)
        values["purchased_at"] = contract.purchased_at.astimezone(timezone.utc).replace(tzinfo=None)
        values["payload_hash"] = contract.payload_hash()
        return self.add("sale_items", dataset=values.pop("dataset_id"),
                        kind=values.pop("source_kind"), **values)

    def test_product_purchase(self):
        self.persist(self.sale())
        self.assertEqual(self.db.scalar(select(s.sale_items.c.purchase_type)), "PRODUCT")

    def test_custom_bom_purchase_has_multiple_ingredients(self):
        self.persist(self.sale("CUSTOM_BOM"))
        rows = list(self.db.execute(select(s.recipe_items.c.quantity_canonical)
                                   .where(s.recipe_items.c.recipe_id == self.recipe)))
        self.assertEqual({r[0] for r in rows}, {Decimal(20), Decimal(15)})
        self.assertIsNone(self.db.scalar(select(s.sale_items.c.product_id)))

    def test_mixed_customer_and_mixed_order_use_same_identity(self):
        self.persist(self.sale())
        self.persist(self.sale("CUSTOM_BOM"))
        rows = list(self.db.execute(select(s.sale_items.c.customer_id, s.sale_items.c.order_id,
                                           s.sale_items.c.purchase_type)))
        self.assertEqual({r[0] for r in rows}, {self.customer})
        self.assertEqual({r[1] for r in rows}, {self.order})
        self.assertEqual({r[2] for r in rows}, {"PRODUCT", "CUSTOM_BOM"})

    def test_customer_cannot_change_within_order(self):
        other = self.add("customers", customer_id=identity(), source_system="research:fixture")
        with self.assertRaises(IntegrityError):
            self.persist(self.sale(customer_id=other))

    def test_wrong_product_for_existing_variant_rejected(self):
        other = self.add("products", source_system="research:fixture", source_product_id=None,
                         product_code="OTHER", name_snapshot="Other", provenance={})
        with self.assertRaises(IntegrityError):
            self.persist(self.sale(product_id=other))

    def test_cross_dataset_customer_recipe_and_unit_references_rejected(self):
        other = self.add_dataset()
        for table, values in [
            ("orders", dict(source_system="research:fixture", source_order_id=identity(),
                            customer_id=self.customer, order_status="COMPLETED", payment_status="PAID")),
            ("recipe_items", dict(recipe_id=self.recipe, ingredient_id=identity(),
                                  ingredient_code_snapshot="NOPE", quantity_original=1,
                                  unit_id=self.gram, unit_symbol_snapshot="g", validation_status="STAGED")),
            ("variants", dict(source_system="research:fixture", source_variant_id=None,
                              product_id=self.product, sku="LEAK", name_snapshot="Leak",
                              sale_unit_id=self.gram, provenance={}))]:
            with self.subTest(table=table), self.assertRaises(IntegrityError):
                self.add(table, dataset=other, **values)

    def test_source_kind_cannot_disagree_with_dataset(self):
        with self.assertRaises(IntegrityError):
            self.add("customers", kind="ACTUAL", customer_id=identity(), source_system="customer")

    def test_duplicate_line_cannot_be_reinserted_with_new_event_or_revision(self):
        contract = self.sale()
        self.persist(contract)
        with self.assertRaises(IntegrityError):
            self.persist(contract.model_copy(update={"source_revision": 2, "source_event_id": uuid4()}))

    def test_reimport_is_read_only_and_same_payload_is_unchanged(self):
        contract = self.sale()
        repo = PocRepository(self.db)
        self.assertEqual(repo.reimport_decision(contract), "NEW")
        self.persist(contract)
        self.assertEqual(repo.reimport_decision(contract), "UNCHANGED")
        with self.assertRaises(ImportConflict):
            repo.reimport_decision(contract.model_copy(update={"quantity": Decimal(2)}))
        self.assertEqual(len(list(self.db.execute(select(s.sale_items)))), 1)

    def test_hash_uses_utc_instants(self):
        a = self.sale()
        b = a.model_copy(update={"purchased_at": a.purchased_at.astimezone(timezone(timedelta(hours=7)))})
        self.assertEqual(a.payload_hash(), b.payload_hash())

    def test_recipe_version_business_key(self):
        with self.assertRaises(IntegrityError):
            self.add("recipes", recipe_family_key="oolong", recipe_version=1, recipe_kind="CUSTOM",
                     recipe_hash="b" * 64, provenance={}, validation_status="STAGED")

    def test_database_rejects_invalid_quantity_and_cross_dimension_claim(self):
        row = dict(self.db.execute(select(s.recipe_items)).mappings().first())
        original_id = row.pop("id")
        for updates in [dict(quantity_original=0), dict(quantity_original=-1),
                        dict(canonical_unit="ml"), dict(quantity_canonical=999)]:
            with self.subTest(updates=updates), self.assertRaises(IntegrityError):
                self.db.execute(update(s.recipe_items).where(s.recipe_items.c.id == original_id)
                                .values(**updates))

    def test_actual_custom_requires_snapshot_and_synthetic_never_has_one(self):
        for updates in [
            dict(source_kind="ACTUAL", source_system="order-service"),
            dict(actual_recipe_snapshot_id=uuid4()),
        ]:
            with self.subTest(updates=updates), self.assertRaises(ValidationError):
                self.sale("CUSTOM_BOM", **updates)

    def test_actual_snapshot_must_match_the_exact_order_line_and_recipe(self):
        dataset = self.add_dataset("ACTUAL")
        customer = self.add("customers", dataset=dataset, kind="ACTUAL",
                            customer_id=identity(), source_system="customer-service")
        order = self.add("orders", dataset=dataset, kind="ACTUAL", source_system="order-service",
                         source_order_id=identity(), customer_id=customer,
                         order_status="COMPLETED", payment_status="PAID")
        unit = self.add("units", dataset=dataset, kind="ACTUAL", source_system="catalog-service",
                        source_unit_id=identity(), symbol="g", dimension="MASS", canonical_unit="g",
                        conversion_factor=1, conversion_rule_version="mass-v1",
                        provenance={}, validation_status="VALIDATED")
        formula = self.add("recipes", dataset=dataset, kind="ACTUAL", recipe_family_key="actual",
                           recipe_version=1, recipe_kind="CUSTOM", recipe_hash="a" * 64,
                           provenance={}, validation_status="VALIDATED")
        ingredient = self.add("ingredients", dataset=dataset, kind="ACTUAL", source_system="catalog-service",
                              source_ingredient_id=identity(), ingredient_code="ACTUAL-TEA",
                              name_snapshot="Tea", provenance={})
        self.add("recipe_items", dataset=dataset, kind="ACTUAL", recipe_id=formula,
                 ingredient_id=ingredient, ingredient_code_snapshot="ACTUAL-TEA",
                 quantity_original=20, unit_id=unit, unit_symbol_snapshot="g",
                 quantity_canonical=20, canonical_unit="g", canonical_factor=1,
                 validation_status="VALIDATED")
        line = identity()
        snapshot = self.add("recipe_snapshots", dataset=dataset, kind="ACTUAL",
                            recipe_id=formula, order_id=order, order_item_id=line,
                            source_system="order-service", source_snapshot_id=identity(),
                            observed_at=datetime(2026, 1, 1), payload_hash="a" * 64, provenance={})
        contract = self.sale("CUSTOM_BOM", dataset_id=dataset, source_kind="ACTUAL",
                             source_system="order-service", customer_id=customer, order_id=order,
                             order_item_id=line, recipe_id=formula, actual_recipe_snapshot_id=snapshot,
                             sale_unit_id=unit)
        # SQLite checks the composite FK; MySQL's additional validation trigger
        # is intentionally not represented as an executed test here.
        self.persist(contract)
        with self.assertRaises(IntegrityError):
            self.persist(contract.model_copy(update={"order_item_id": uuid4()}))

    def test_dimension_and_canonical_snapshot_constraints_are_database_enforced(self):
        for dimension, symbol, canonical, factor in [
            ("MASS", "kg", "g", 1), ("MASS", "ml", "ml", 1), ("COUNT", "box24", "count", 24)]:
            with self.subTest(symbol=symbol), self.assertRaises(IntegrityError):
                self.add("units", source_system="research:fixture", source_unit_id=None,
                         symbol=symbol, dimension=dimension, canonical_unit=canonical,
                         conversion_factor=factor, conversion_rule_version="bad",
                         provenance={}, validation_status="VALIDATED")

    def test_feedback_separates_preference_and_operational_return(self):
        sale = self.persist(self.sale())
        values = dict(sale_item_id=sale, source_system="research:fixture",
                      source_feedback_id=identity(), feedback_type="OPERATIONAL_RETURN",
                      return_reason="DAMAGED", feedback_time=datetime(2026, 1, 2))
        self.add("feedback", **values)
        with self.assertRaises(IntegrityError):
            self.add("feedback", **{**values, "source_feedback_id": identity(), "rating": 1})
        with self.assertRaises(IntegrityError):
            self.add("feedback", **{**values, "source_feedback_id": identity(), "return_reason": "CHANGE_OF_MIND"})

    def test_reads_require_dataset_kind_and_published_status(self):
        self.persist(self.sale())
        repo = PocRepository(self.db)
        self.assertEqual(repo.sales_for_customer(self.dataset, self.customer, source_kind="SYNTHETIC"), [])
        self.db.execute(update(s.datasets).where(s.datasets.c.id == self.dataset).values(status="PUBLISHED"))
        self.assertEqual(len(repo.sales_for_customer(self.dataset, self.customer, source_kind="SYNTHETIC")), 1)
        self.assertEqual(repo.sales_for_customer(identity(), self.customer, source_kind="SYNTHETIC"), [])
        self.assertEqual(repo.sales_for_customer(self.dataset, self.customer, source_kind="ACTUAL"), [])

    def test_synthetic_is_invisible_to_v1_catalog_and_metadata(self):
        from app.database.base import Base
        from app.models.category import Category
        from app.models.ingredient import Ingredient
        from app.models.product import Product
        from app.models.product_ingredient import ProductIngredient
        from app.repositories.v1_recommendation_repository import V1RecommendationRepository
        self.assertIsNot(s.metadata, Base.metadata)
        self.assertFalse(any(t.startswith("ingredient_poc_") for t in Base.metadata.tables))
        for table in [Category.__table__, Ingredient.__table__, Product.__table__, ProductIngredient.__table__]:
            # Adapt only the emitted test DDL; never mutate production metadata.
            ddl = str(CreateTable(table).compile(dialect=self.engine.dialect))
            self.db.exec_driver_sql(ddl.replace("CURRENT_TIMESTAMP(6)", "CURRENT_TIMESTAMP"))
        with Session(bind=self.db) as session:
            self.assertEqual(V1RecommendationRepository().active_catalog(session), ())


class MigrationTests(unittest.TestCase):
    def test_mysql_datetime_default_matches_fractional_precision(self):
        sql = mysql_migration()
        self.assertEqual(len(re.findall(r"created_at DATETIME\(6\) NOT NULL DEFAULT CURRENT_TIMESTAMP\(6\)", sql)), 12)

    def test_reviewed_migration_matches_metadata_and_guards(self):
        folder = Path(__file__).resolve().parents[1] / "flyway/sql"
        paths = list(folder.glob("V*__ingredient_poc_schema.sql"))
        self.assertEqual(len(paths), 1)
        sql = paths[0].read_text(encoding="utf-8")
        self.assertEqual(sql, mysql_migration())
        self.assertEqual(len(re.findall(r"CREATE TABLE ingredient_poc_", sql)), 12)
        self.assertFalse(re.search(r"(?im)^\s*(ALTER TABLE|DROP |INSERT INTO|UPDATE ingredient_|DELETE FROM)", sql))
        self.assertFalse(re.search(r"REFERENCES (?!ingredient_poc_)", sql))
        self.assertNotIn("CASCADE", sql)

    def test_all_children_have_database_freeze_guards(self):
        sql = mysql_migration()
        for table in s.metadata.tables.values():
            short = table.name.removeprefix("ingredient_poc_")
            for op in ("insert", "update", "delete"):
                self.assertIn("CREATE TRIGGER tr_ip_" + short + "_" + op, sql)
        self.assertIn("OLD.status IN ('PUBLISHED','REJECTED')", sql)
        self.assertIn("FOR UPDATE", sql)
        self.assertIn("actual snapshot is append-only", sql)
        self.assertIn("referenced recipe snapshot is immutable", sql)
        self.assertIn("referenced unit snapshot is immutable", sql)

    @unittest.skip("No configured, independently verified isolated MySQL/Flyway database; shared DB prohibited")
    def test_mysql_flyway_execution(self):
        pass


class IntegrationIsolationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(__file__).resolve().parents[1] / "scripts/run_ingredient_poc_mysql_tests.py"
        spec = importlib.util.spec_from_file_location("poc_mysql_runner_unit_fixture", path)
        cls.runner = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = cls.runner
        spec.loader.exec_module(cls.runner)

    def test_test_context_rejects_shared_database_names(self):
        with self.assertRaises(ValueError):
            self.runner.IsolatedMySQL(3306, "fixture", ("recommend_app", "other_db"), identity())

    def test_connect_rejects_database_outside_owned_scope_before_network(self):
        context = self.runner.IsolatedMySQL(
            33306, "fixture", ("ingredient_poc_fresh_aaaaaaaaaaaa", "ingredient_poc_upgrade_aaaaaaaaaaaa"),
            identity())
        with self.assertRaises(ValueError):
            context.connect("recommend_app")


if __name__ == "__main__":
    unittest.main()
