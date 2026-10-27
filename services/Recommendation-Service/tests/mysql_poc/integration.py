"""Executed only by the owned-Docker runner, not ordinary test discovery."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from decimal import Decimal
import json
from threading import Event
import time
import unittest
from uuid import uuid4

import pymysql
from sqlalchemy import insert, select, update
from sqlalchemy.exc import DBAPIError

from app.ingredient_poc import schema as s
from app.ingredient_poc.contracts import RecipeContract, RecipeItemContract, SaleContract, UnitContract, recipe_hash
from app.ingredient_poc.repository import ImportConflict, PocRepository

FIXTURE = None
EVIDENCE = None


def key():
    return str(uuid4())


def normalize(value):
    if isinstance(value, dict):
        return json.dumps(value, sort_keys=True)
    if isinstance(value, datetime):
        return value.astimezone(timezone.utc).replace(tzinfo=None) if value.tzinfo else value
    return str(value) if not isinstance(value, (str, int, Decimal, type(None))) else value


class MySQLTests(unittest.TestCase):
    def setUp(self):
        assert FIXTURE is not None and EVIDENCE is not None, "use isolated runner"
        self.engine = FIXTURE.engine(FIXTURE.databases[0])
        self.db = self.engine.connect()
        self.tx = self.db.begin()
        self.f = self.build()

    def tearDown(self):
        if self.tx is not None and self.tx.is_active:
            self.tx.rollback()
        self.db.close()
        self.engine.dispose()

    def add(self, table, f, **values):
        identifier = values.pop("id", key())
        self.db.execute(insert(s.metadata.tables["ingredient_poc_" + table]).values(
            id=identifier, dataset_id=f["dataset"], source_kind=f["kind"], **values))
        return identifier

    def build(self, kind="SYNTHETIC"):
        f = {"dataset": key(), "kind": kind}
        source = "research:integration" if kind == "SYNTHETIC" else "fixture:source"
        f["source"] = source
        self.db.execute(insert(s.datasets).values(
            id=f["dataset"], dataset_name="test-" + f["dataset"], schema_version=1,
            source_kind=kind, source_system=source, source_snapshot_hash="a" * 64,
            status="STAGED", validation_policy_version="integration-v1", manifest_json={}))
        f["customer"] = self.add("customers", f, customer_id=key(), source_system=source)
        for name, dimension, canonical in [("g", "MASS", "g"), ("pack", "COUNT", "count")]:
            f[name] = self.add("units", f, source_system=source,
                               source_unit_id=None if kind == "SYNTHETIC" else key(),
                               symbol=name, dimension=dimension, canonical_unit=canonical,
                               conversion_factor=1, conversion_rule_version="fixture-v1",
                               provenance={}, validation_status="VALIDATED")
        f["product"] = self.add("products", f, source_system=source,
                                source_product_id=None if kind == "SYNTHETIC" else key(),
                                product_code="TEA", name_snapshot="Research tea", provenance={})
        f["variant"] = self.add("variants", f, source_system=source,
                                source_variant_id=None if kind == "SYNTHETIC" else key(),
                                product_id=f["product"], sku="TEA-PACK", name_snapshot="Research pack",
                                sale_unit_id=f["pack"], provenance={})
        f["order"] = self.add("orders", f, source_system=source, source_order_id=key(),
                              customer_id=f["customer"], order_status="COMPLETED", payment_status="PAID")
        unit = UnitContract(symbol="g", dimension="MASS", canonical_unit="g", conversion_factor=1,
                            conversion_rule_version="fixture-v1")
        contract = RecipeContract(
            recipe_family_key="oolong-jasmine", recipe_version=1, recipe_kind="CUSTOM",
            effective_time_unknown=True,
            items=tuple(RecipeItemContract(ingredient_namespace=source, ingredient_code=code,
                                            quantity_original=q, quantity_canonical=q, unit=unit)
                        for code, q in [("OOLONG", 20), ("SUGAR", 15), ("JASMINE", 2)]))
        f["recipe"] = self.add("recipes", f, recipe_family_key=contract.recipe_family_key,
                               recipe_version=1, recipe_kind="CUSTOM", recipe_hash=recipe_hash(contract),
                               provenance={"fixture": True}, validation_status="STAGED")
        f["item_ids"] = []
        for code, quantity in [("OOLONG", 20), ("SUGAR", 15), ("JASMINE", 2)]:
            ingredient = self.add("ingredients", f, source_system=source,
                                  source_ingredient_id=None if kind == "SYNTHETIC" else key(),
                                  ingredient_code=code, name_snapshot=code, provenance={})
            f["item_ids"].append(self.add(
                "recipe_items", f, recipe_id=f["recipe"], ingredient_id=ingredient,
                ingredient_code_snapshot=code, quantity_original=quantity, unit_id=f["g"],
                unit_symbol_snapshot="g", quantity_canonical=quantity, canonical_unit="g",
                canonical_factor=1, validation_status="VALIDATED"))
        self.db.execute(update(s.recipes).where(s.recipes.c.id == f["recipe"])
                        .values(validation_status="VALIDATED"))
        f["recipe_hash"] = recipe_hash(contract)
        return f

    def sale(self, purchase_type="PRODUCT", f=None, **overrides):
        f = f or self.f
        values = dict(dataset_id=f["dataset"], customer_id=f["customer"], order_id=f["order"],
                      order_item_id=key(), purchase_type=purchase_type,
                      product_id=f["product"] if purchase_type == "PRODUCT" else None,
                      variant_id=f["variant"] if purchase_type == "PRODUCT" else None,
                      recipe_id=f["recipe"] if purchase_type == "CUSTOM_BOM" else None,
                      recipe_kind="CUSTOM" if purchase_type == "CUSTOM_BOM" else None,
                      sale_unit_id=f["pack"], quantity=Decimal(1),
                      purchased_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
                      order_status="COMPLETED", payment_status="PAID", source_kind=f["kind"],
                      source_system=f["source"], source_revision=1)
        values.update(overrides)
        return SaleContract(**values)

    def sale_values(self, contract):
        values = {k: normalize(v) for k, v in contract.model_dump().items()}
        values.update(id=key(), payload_hash=contract.payload_hash())
        return values

    def persist(self, contract):
        values = self.sale_values(contract)
        self.db.execute(insert(s.sale_items).values(**values))
        return values["id"]

    def reject(self, operation, codes=(1452, 3819, 1644, 1062)):
        try:
            with self.db.begin_nested():
                operation()
        except DBAPIError as error:
            self.assertIn(error.orig.args[0], codes)
        else:
            self.fail("invalid data unexpectedly accepted")

    def lifecycle(self, status, *, f=None, manifest=None):
        values = {"status": status}
        if manifest is not None:
            values["manifest_json"] = manifest
        return self.db.execute(update(s.datasets).where(s.datasets.c.id == (f or self.f)["dataset"]).values(**values))

    def publish(self, f=None):
        self.lifecycle("VALIDATED", f=f, manifest={"validation_complete": True})
        self.lifecycle("PUBLISHED", f=f)

    def test_product_custom_and_mixed_order(self):
        self.persist(self.sale())
        self.persist(self.sale("CUSTOM_BOM"))
        rows = list(self.db.execute(select(s.sale_items).where(s.sale_items.c.dataset_id == self.f["dataset"])).mappings())
        self.assertEqual(len(rows), 2)
        self.assertEqual({r["customer_id"] for r in rows}, {self.f["customer"]})
        self.assertEqual({r["order_id"] for r in rows}, {self.f["order"]})
        self.assertEqual({r["purchase_type"] for r in rows}, {"PRODUCT", "CUSTOM_BOM"})
        items = list(self.db.execute(select(s.recipe_items).where(s.recipe_items.c.recipe_id == self.f["recipe"])).mappings())
        self.assertEqual({r["quantity_canonical"] for r in items}, {Decimal(20), Decimal(15), Decimal(2)})
        self.assertEqual(self.db.scalar(select(s.recipes.c.recipe_hash).where(s.recipes.c.id == self.f["recipe"])), self.f["recipe_hash"])

    def test_invalid_quantity_unit_customer_product_status(self):
        item = self.f["item_ids"][0]
        for values in [dict(quantity_original=0), dict(quantity_original=-1),
                       dict(canonical_unit="ml"), dict(quantity_canonical=99)]:
            self.reject(lambda values=values: self.db.execute(
                update(s.recipe_items).where(s.recipe_items.c.id == item).values(**values)))
        other_product = self.add("products", self.f, source_system=self.f["source"], source_product_id=None,
                                 product_code="OTHER", name_snapshot="Other", provenance={})
        self.reject(lambda: self.persist(self.sale(product_id=other_product)))
        customer = self.add("customers", self.f, customer_id=key(), source_system=self.f["source"])
        self.reject(lambda: self.persist(self.sale(customer_id=customer)))
        self.reject(lambda: self.add("orders", self.f, source_system=self.f["source"], source_order_id=key(),
                                    customer_id=customer, order_status="PENDING", payment_status="UNPAID"))

    def test_empty_or_unvalidated_recipe_rejected_by_trigger(self):
        empty = self.add("recipes", self.f, recipe_family_key="empty", recipe_version=1, recipe_kind="CUSTOM",
                         recipe_hash="a" * 64, provenance={}, validation_status="VALIDATED")
        self.reject(lambda: self.persist(self.sale("CUSTOM_BOM", recipe_id=empty)), (1644,))
        self.db.execute(update(s.recipes).where(s.recipes.c.id == self.f["recipe"]).values(validation_status="STAGED"))
        self.reject(lambda: self.persist(self.sale("CUSTOM_BOM")), (1644,))

    def test_cross_dataset_references_rejected(self):
        other = self.build()
        self.reject(lambda: self.persist(self.sale("CUSTOM_BOM", recipe_id=other["recipe"])), (1452,))
        original_item = dict(self.db.execute(select(s.recipe_items).where(s.recipe_items.c.id == self.f["item_ids"][0])).mappings().one())
        foreign_ingredient = self.db.scalar(select(s.recipe_items.c.ingredient_id).where(s.recipe_items.c.id == other["item_ids"][0]))
        original_item.update(id=key(), ingredient_id=foreign_ingredient)
        self.reject(lambda: self.db.execute(insert(s.recipe_items).values(**original_item)))

    def test_actual_snapshot_exact_line_and_no_synthetic_alias(self):
        f = self.build("ACTUAL")
        line = key()
        snapshot = self.add("recipe_snapshots", f, recipe_id=f["recipe"], order_id=f["order"],
                            order_item_id=line, source_system=f["source"], source_snapshot_id=key(),
                            observed_at=datetime(2026, 1, 1), payload_hash="b" * 64, provenance={})
        self.persist(self.sale("CUSTOM_BOM", f, order_item_id=line, actual_recipe_snapshot_id=snapshot))
        self.reject(lambda: self.persist(self.sale("CUSTOM_BOM", f, actual_recipe_snapshot_id=snapshot)), (1452,))
        self.reject(lambda: self.add("recipe_snapshots", self.f, recipe_id=self.f["recipe"],
                                    order_id=self.f["order"], order_item_id=key(), source_system=self.f["source"],
                                    source_snapshot_id=key(), observed_at=datetime(2026, 1, 1),
                                    payload_hash="c" * 64, provenance={}), (3819,))

    def test_business_duplicate_and_reimport(self):
        contract = self.sale()
        repo = PocRepository(self.db)
        self.assertEqual(repo.reimport_decision(contract), "NEW")
        self.persist(contract)
        self.assertEqual(repo.reimport_decision(contract), "UNCHANGED")
        self.reject(lambda: self.persist(contract.model_copy(update={"source_event_id": uuid4()})), (1062,))
        with self.assertRaises(ImportConflict):
            repo.reimport_decision(contract.model_copy(update={"source_revision": 2}))

    def test_lifecycle_publish_manifest_and_immutability(self):
        for manifest in [{}, {"validation_complete": False}, {"validation_complete": "true"},
                         {"validation_complete": 1}]:
            self.reject(lambda manifest=manifest: self.lifecycle("VALIDATED", manifest=manifest), (1644,))
        self.persist(self.sale("CUSTOM_BOM"))
        self.publish()
        self.assertEqual(len(PocRepository(self.db).sales_for_customer(
            self.f["dataset"], self.f["customer"], source_kind="SYNTHETIC")), 1)
        self.reject(lambda: self.persist(self.sale()), (1644,))
        self.reject(lambda: self.lifecycle("STAGED"), (1644,))
        self.reject(lambda: self.lifecycle("REJECTED"), (1644,))
        self.reject(lambda: self.db.execute(s.datasets.delete().where(s.datasets.c.id == self.f["dataset"])), (1644,))
        self.reject(lambda: self.db.execute(update(s.recipes).where(s.recipes.c.id == self.f["recipe"]).values(recipe_version=2)), (1644,))

    def test_reject_transitions_and_source_identity(self):
        self.reject(lambda: self.lifecycle("PUBLISHED"), (1644,))
        self.reject(lambda: self.db.execute(update(s.datasets).where(s.datasets.c.id == self.f["dataset"])
                                           .values(source_kind="ACTUAL")), (1644,))
        self.lifecycle("REJECTED")
        other = self.build()
        self.lifecycle("VALIDATED", f=other, manifest={"validation_complete": True})
        self.lifecycle("REJECTED", f=other)

    def test_referenced_recipe_item_and_unit_frozen(self):
        self.persist(self.sale("CUSTOM_BOM"))
        self.reject(lambda: self.db.execute(update(s.recipes).where(s.recipes.c.id == self.f["recipe"]).values(recipe_version=2)), (1644,))
        self.reject(lambda: self.db.execute(update(s.recipe_items).where(s.recipe_items.c.id == self.f["item_ids"][0])
                                           .values(quantity_original=21, quantity_canonical=21)), (1644,))
        self.reject(lambda: self.db.execute(update(s.units).where(s.units.c.id == self.f["g"])
                                           .values(conversion_rule_version="mutated")), (1644,))

    def test_live_introspection_matches_all_counts_and_metadata(self):
        from sqlalchemy import inspect
        inspector = inspect(self.db)
        names = [n for n in inspector.get_table_names() if n.startswith("ingredient_poc_")]
        self.assertEqual(set(names), set(s.metadata.tables))
        totals = {"tables": len(names), "columns": 0, "checks": 0, "foreign_keys": 0,
                  "unique_constraints": 0, "explicit_indexes": 0}
        auto_indexes = []
        for table in s.metadata.sorted_tables:
            columns = inspector.get_columns(table.name)
            totals["columns"] += len(columns)
            self.assertEqual({c["name"] for c in columns}, set(table.columns.keys()))
            for c in columns:
                expected = table.c[c["name"]]
                self.assertEqual(c["nullable"], expected.nullable)
                # MySQL reports NUMERIC as DECIMAL; dialect-normalized affinities.
                self.assertEqual(c["type"]._type_affinity, expected.type._type_affinity)
            totals["checks"] += len(inspector.get_check_constraints(table.name))
            totals["foreign_keys"] += len(inspector.get_foreign_keys(table.name))
            totals["unique_constraints"] += len(inspector.get_unique_constraints(table.name))
            actual = {i["name"] for i in inspector.get_indexes(table.name)}
            explicit = {i.name for i in table.indexes}
            self.assertTrue(explicit <= actual)
            totals["explicit_indexes"] += len(explicit)
            auto_indexes.extend(sorted(actual - explicit))
        rows = self.db.exec_driver_sql("SELECT TRIGGER_NAME,ACTION_TIMING FROM information_schema.triggers WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE LIKE 'ingredient_poc_%%'").mappings().all()
        totals["before_triggers"] = sum(r["ACTION_TIMING"] == "BEFORE" for r in rows)
        self.assertEqual(totals, dict(tables=12, columns=146, checks=42, foreign_keys=28,
                                     unique_constraints=36, explicit_indexes=17, before_triggers=36))
        EVIDENCE["introspection"] = {"totals": totals, "other_physical_indexes": auto_indexes}

    def committed(self):
        self.tx.commit()
        self.tx = None

    def transaction(self, operation, *, attempted=None, retrying=None):
        """Bounded full-transaction retry; only lock conflicts are retryable."""
        for attempt in range(1, 4):
            c = FIXTURE.connect(FIXTURE.databases[0])
            try:
                if attempted is not None:
                    attempted.set()
                with c.cursor() as q:
                    q.execute("SELECT status FROM ingredient_poc_datasets WHERE id=%s FOR UPDATE",
                              (self.f["dataset"],))
                    operation(q)
                c.commit()
                return {"code": 0, "attempts": attempt}
            except pymysql.MySQLError as error:
                c.rollback()
                code = error.args[0]
                if code in (1205, 1213) and attempt < 3:
                    if retrying is not None:
                        retrying.set()
                    time.sleep(0.05 * attempt)
                    continue
                return {"code": code, "attempts": attempt}
            finally:
                c.close()
        raise AssertionError("unreachable retry state")

    def direct_insert_sale(self, cursor, contract):
        values = self.sale_values(contract)
        columns = list(values)
        cursor.execute("INSERT INTO ingredient_poc_sale_items ("
                       + ",".join(columns) + ") VALUES (" + ",".join(["%s"] * len(columns)) + ")",
                       tuple(values[k] for k in columns))

    def pair(self, first, second, *, expected):
        held = Event()
        release = Event()
        attempted = Event()

        def first_operation(cursor):
            first(cursor)
            held.set()
            if not release.wait(5):
                raise AssertionError("test barrier timeout")

        with ThreadPoolExecutor(max_workers=2) as pool:
            a = pool.submit(self.transaction, first_operation)
            try:
                self.assertTrue(held.wait(5), "first transaction did not reach barrier")
                b = pool.submit(self.transaction, second, attempted=attempted)
                self.assertTrue(attempted.wait(3))
                self.assertFalse(b.done(), "second transaction should wait for dataset lock")
                deadline = time.monotonic() + 2
                observed = False
                with FIXTURE.connect(FIXTURE.databases[0]) as monitor:
                    while time.monotonic() < deadline:
                        with monitor.cursor() as q:
                            q.execute("SELECT COUNT(*) AS n FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT'")
                            if q.fetchone()["n"]:
                                observed = True
                                break
                        time.sleep(0.04)
                self.assertTrue(observed, "no real InnoDB lock wait was observed")
            finally:
                release.set()
            outcomes = [a.result(timeout=12), b.result(timeout=12)]
        self.assertEqual([r["code"] for r in outcomes], expected)
        EVIDENCE.setdefault("concurrency", []).append(
            {"case": self._testMethodName, "outcomes": outcomes, "innodb_lock_wait_observed": observed})

    def test_concurrent_duplicate_business_line(self):
        contract = self.sale()
        self.committed()
        self.pair(lambda q: self.direct_insert_sale(q, contract),
                  lambda q: self.direct_insert_sale(q, contract), expected=[0, 1062])
        with FIXTURE.connect(FIXTURE.databases[0]) as c:
            with c.cursor() as q:
                q.execute("SELECT COUNT(*) AS n FROM ingredient_poc_sale_items WHERE dataset_id=%s",
                          (self.f["dataset"],))
                self.assertEqual(q.fetchone()["n"], 1)

    def test_concurrent_different_events_same_business_line(self):
        a = self.sale(source_event_id=uuid4())
        b = a.model_copy(update={"source_event_id": uuid4()})
        self.committed()
        self.pair(lambda q: self.direct_insert_sale(q, a),
                  lambda q: self.direct_insert_sale(q, b), expected=[0, 1062])

    def test_concurrent_referenced_recipe_updates_rejected(self):
        self.persist(self.sale("CUSTOM_BOM"))
        self.committed()

        def operation(q):
            q.execute("UPDATE ingredient_poc_recipes SET recipe_version=2 WHERE id=%s", (self.f["recipe"],))

        start = Event()

        def worker():
            self.assertTrue(start.wait(3))
            return self.transaction(operation)

        with ThreadPoolExecutor(max_workers=2) as pool:
            a, b = pool.submit(worker), pool.submit(worker)
            start.set()
            outcomes = [a.result(timeout=12), b.result(timeout=12)]
        self.assertEqual([r["code"] for r in outcomes], [1644, 1644])
        EVIDENCE.setdefault("concurrency", []).append({"case": self._testMethodName, "outcomes": outcomes})

    def publish_sql(self, q):
        q.execute("UPDATE ingredient_poc_datasets SET status='VALIDATED',manifest_json=%s WHERE id=%s",
                  (json.dumps({"validation_complete": True}), self.f["dataset"]))
        q.execute("UPDATE ingredient_poc_datasets SET status='PUBLISHED' WHERE id=%s", (self.f["dataset"],))

    def test_concurrent_publish_then_sale_insert(self):
        sale = self.sale()
        self.committed()
        self.pair(self.publish_sql, lambda q: self.direct_insert_sale(q, sale), expected=[0, 1644])
        with FIXTURE.connect(FIXTURE.databases[0]) as c:
            with c.cursor() as q:
                q.execute("SELECT status FROM ingredient_poc_datasets WHERE id=%s", (self.f["dataset"],))
                self.assertEqual(q.fetchone()["status"], "PUBLISHED")
                q.execute("SELECT COUNT(*) AS n FROM ingredient_poc_sale_items WHERE dataset_id=%s", (self.f["dataset"],))
                self.assertEqual(q.fetchone()["n"], 0)

    def test_concurrent_recipe_append_then_publish(self):
        ingredient = self.add("ingredients", self.f, source_system=self.f["source"],
                              source_ingredient_id=None, ingredient_code="HONEY",
                              name_snapshot="Research honey", provenance={})
        self.committed()
        unit = UnitContract(symbol="g", dimension="MASS", canonical_unit="g", conversion_factor=1,
                            conversion_rule_version="fixture-v1")
        final_recipe = RecipeContract(
            recipe_family_key="oolong-jasmine", recipe_version=1, recipe_kind="CUSTOM",
            effective_time_unknown=True,
            items=tuple(RecipeItemContract(ingredient_namespace=self.f["source"], ingredient_code=code,
                                            quantity_original=qty, quantity_canonical=qty, unit=unit)
                        for code, qty in [("OOLONG", 20), ("SUGAR", 15), ("JASMINE", 2), ("HONEY", 1)]))
        expected_hash = recipe_hash(final_recipe)

        def append(q):
            q.execute("INSERT INTO ingredient_poc_recipe_items "
                      "(id,dataset_id,source_kind,recipe_id,ingredient_id,ingredient_code_snapshot,"
                      "quantity_original,unit_id,unit_symbol_snapshot,quantity_canonical,canonical_unit,"
                      "canonical_factor,validation_status) VALUES (%s,%s,'SYNTHETIC',%s,%s,'HONEY',1,%s,'g',1,'g',1,'VALIDATED')",
                      (key(), self.f["dataset"], self.f["recipe"], ingredient, self.f["g"]))
            q.execute("UPDATE ingredient_poc_recipes SET recipe_hash=%s WHERE id=%s",
                      (expected_hash, self.f["recipe"]))

        def publish(q):
            q.execute("SELECT COUNT(*) AS n FROM ingredient_poc_recipe_items WHERE recipe_id=%s", (self.f["recipe"],))
            self.assertEqual(q.fetchone()["n"], 4)
            q.execute("SELECT recipe_hash FROM ingredient_poc_recipes WHERE id=%s", (self.f["recipe"],))
            self.assertEqual(q.fetchone()["recipe_hash"], expected_hash)
            self.publish_sql(q)

        self.pair(append, publish, expected=[0, 0])

    def test_stale_read_view_cannot_mutate_newly_referenced_recipe(self):
        self.committed()
        stale = FIXTURE.connect(FIXTURE.databases[0])
        try:
            with stale.cursor() as q:
                q.execute("SELECT COUNT(*) AS n FROM ingredient_poc_sale_items WHERE recipe_id=%s", (self.f["recipe"],))
                self.assertEqual(q.fetchone()["n"], 0)
            result = self.transaction(lambda q: self.direct_insert_sale(q, self.sale("CUSTOM_BOM")))
            self.assertEqual(result["code"], 0)
            try:
                with stale.cursor() as q:
                    q.execute("UPDATE ingredient_poc_recipes SET recipe_version=2 WHERE id=%s", (self.f["recipe"],))
            except pymysql.MySQLError as error:
                self.assertEqual(error.args[0], 1644)
            else:
                self.fail("stale read view bypassed referenced-recipe immutability")
        finally:
            stale.rollback()
            stale.close()

    def test_lock_timeout_rolls_back_and_retries_whole_transaction(self):
        contract = self.sale()
        self.committed()
        holder = FIXTURE.connect(FIXTURE.databases[0])
        retrying = Event()
        try:
            with holder.cursor() as q:
                q.execute("SELECT status FROM ingredient_poc_datasets WHERE id=%s FOR UPDATE", (self.f["dataset"],))
            with ThreadPoolExecutor(max_workers=1) as pool:
                pending = pool.submit(self.transaction,
                                      lambda q: self.direct_insert_sale(q, contract), retrying=retrying)
                try:
                    self.assertTrue(retrying.wait(6), "expected bounded InnoDB lock timeout")
                finally:
                    holder.rollback()
                result = pending.result(timeout=12)
            self.assertEqual(result, {"code": 0, "attempts": 2})
            EVIDENCE.setdefault("concurrency", []).append(
                {"case": self._testMethodName, "forced_error": 1205, "outcome": result})
        finally:
            holder.rollback()
            holder.close()

    def test_invalid_sale_shapes_rejected_by_mysql_without_python_validation(self):
        base = self.sale_values(self.sale())
        for change in [dict(quantity=0), dict(quantity=-1), dict(variant_id=None),
                       dict(order_status="PENDING"), dict(payment_status="UNPAID")]:
            self.reject(lambda change=change: self.db.execute(insert(s.sale_items).values(
                **{**base, "id": key(), "order_item_id": key(), **change})))
        custom = self.sale_values(self.sale("CUSTOM_BOM"))
        self.reject(lambda: self.db.execute(insert(s.sale_items).values(
            **{**custom, "product_id": self.f["product"], "variant_id": self.f["variant"]})), (3819,))
