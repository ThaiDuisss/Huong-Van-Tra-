"""Full Phase 2 integration, exclusively loaded by the owned-Docker runner."""
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from decimal import Decimal
import json
import secrets
import unittest
from unittest.mock import patch
from uuid import uuid4

import pymysql
from sqlalchemy import insert, select, text, update
from sqlalchemy.exc import DBAPIError

from app.ingredient_poc import schema as s
from app.ingredient_poc import synthetic_store as storage
from app.ingredient_poc.contracts import content_hash
from app.ingredient_poc.repository import ImportConflict
from app.ingredient_poc.synthetic import (
    T1, T2, canonical_json, generate, loads, sale_contract, seal, split_view, write_artifacts,
)
from app.ingredient_poc.synthetic_validation import DatasetInvalid, validate

FIXTURE = EVIDENCE = ARTIFACT_ROOT = SHARED_UUID = None


class SyntheticMySQLTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        assert FIXTURE and EVIDENCE is not None and ARTIFACT_ROOT and SHARED_UUID, "use owned runner"
        db = FIXTURE.databases[0]
        tag = db.rsplit("_", 1)[1]
        username, password = "poc_writer_" + tag, secrets.token_hex(24)
        with FIXTURE.connect(db) as conn:
            with conn.cursor() as q:
                q.execute("CREATE USER %s@'%%' IDENTIFIED BY %s", (username, password))
                for table in s.metadata.sorted_tables:
                    q.execute("GRANT SELECT,INSERT,UPDATE ON `" + db + "`.`" + table.name + "` TO %s@'%%'", (username,))
                q.execute("SHOW GRANTS FOR %s@'%%'", (username,))
                grants = q.fetchall()
            conn.commit()
        EVIDENCE["phase2_writer_grants"] = grants
        cls.target = storage.ResearchTarget(FIXTURE.port, db, FIXTURE.server_uuid, SHARED_UUID, username, password)
        cls.store = storage.SyntheticStore(cls.target)
        cls.bundle = generate()
        cls.dataset_id = cls.bundle["dataset"]["id"]
        cls.validation = validate(cls.bundle)
        cls.artifact_hashes = write_artifacts(cls.bundle, ARTIFACT_ROOT / "synthetic-dataset")
        EVIDENCE["phase2_dataset"] = {"dataset_id": cls.dataset_id,
                                    "source_snapshot_hash": cls.bundle["dataset"]["source_snapshot_hash"],
                                    "artifact_directory": "synthetic-dataset", "artifact_checksums": cls.artifact_hashes,
                                    "validation": cls.validation}
        cls.v1_before = cls.v1_counts()
        cls.import_result = cls.store.import_dataset(cls.bundle)

    @classmethod
    def tearDownClass(cls):
        if hasattr(cls, "store"):
            cls.store.close()

    @classmethod
    def v1_counts(cls):
        with FIXTURE.connect(FIXTURE.databases[0]) as conn:
            with conn.cursor() as q:
                result = {}
                for table in ("products", "product_variants", "ingredients", "product_ingredients", "interaction_events",
                              "customer_interactions", "recommendation_snapshots", "report_purchase_orders", "business_report_exports"):
                    q.execute("SELECT COUNT(*) AS n FROM " + table)
                    result[table] = q.fetchone()["n"]
            conn.rollback()
        return result

    def query(self, sql, params=None):
        with FIXTURE.connect(FIXTURE.databases[0]) as conn:
            with conn.cursor() as q:
                q.execute(sql, params)
                result = q.fetchall()
            conn.rollback()
        return result

    def test_01_full_import_and_independent_sql_counts(self):
        self.assertEqual("NEW", self.import_result["decision"])
        actual = {}
        for short in ("customers", "recipes", "orders", "sale_items", "products", "variants", "recipe_items"):
            actual[short] = self.query("SELECT COUNT(*) AS n FROM ingredient_poc_" + short + " WHERE dataset_id=%s", (self.dataset_id,))[0]["n"]
        self.assertEqual({"customers": 400, "recipes": 80, "orders": 2520, "sale_items": 4000,
                          "products": 60, "variants": 60, "recipe_items": 348}, actual)
        sale_types = self.query("SELECT purchase_type,COUNT(*) AS n FROM ingredient_poc_sale_items WHERE dataset_id=%s GROUP BY purchase_type", (self.dataset_id,))
        self.assertEqual({"PRODUCT": 1600, "CUSTOM_BOM": 2400}, {r["purchase_type"]: r["n"] for r in sale_types})
        cohorts = self.query("""SELECT cohort,COUNT(*) AS n FROM (
            SELECT customer_id,CASE WHEN COUNT(DISTINCT purchase_type)=2 THEN 'BOTH'
            WHEN MIN(purchase_type)='PRODUCT' THEN 'PRODUCT_ONLY' ELSE 'BOM_ONLY' END AS cohort
            FROM ingredient_poc_sale_items WHERE dataset_id=%s GROUP BY customer_id
            ) AS c GROUP BY cohort""", (self.dataset_id,))
        self.assertEqual({"PRODUCT_ONLY": 120, "BOM_ONLY": 120, "BOTH": 160}, {r["cohort"]: r["n"] for r in cohorts})
        EVIDENCE["phase2_sql_counts"] = {"tables": actual, "purchase_types": sale_types, "cohorts": cohorts}

    def test_02_reimport_is_noop_with_unchanged_counts_checksums(self):
        before = self.store.inspect(self.bundle)
        result = self.store.import_dataset(self.bundle)
        after = self.store.inspect(self.bundle)
        self.assertEqual("UNCHANGED", result["decision"])
        self.assertEqual(before, after)
        EVIDENCE["phase2_reimport"] = {"decision": result["decision"], "checksums_unchanged": True}

    def test_03_changed_business_payload_conflicts_and_rolls_back(self):
        conflict = deepcopy(self.bundle)
        sale = conflict["tables"]["sale_items"][0]
        sale["quantity"] = Decimal(2)
        sale["payload_hash"] = sale_contract(sale).payload_hash()
        seal(conflict)
        self.assertEqual("PASS", validate(conflict)["status"])
        before = self.store.inspect(self.bundle)
        with self.assertRaises(ImportConflict):
            self.store.import_dataset(conflict)
        self.assertEqual(before, self.store.inspect(self.bundle))

    def test_04_publish_rechecks_database_not_only_validation_flag(self):
        product = self.bundle["tables"]["products"][0]
        with self.store.engine.begin() as conn:
            storage._locked_dataset(conn, self.dataset_id)
            conn.execute(update(s.products).where(s.products.c.id == product["id"]).values(name_snapshot="tampered staged data"))
        try:
            with self.assertRaises(ImportConflict):
                self.store.publish(self.bundle)
            self.assertEqual("STAGED", self.query("SELECT status FROM ingredient_poc_datasets WHERE id=%s", (self.dataset_id,))[0]["status"])
        finally:
            with self.store.engine.begin() as conn:
                storage._locked_dataset(conn, self.dataset_id)
                conn.execute(update(s.products).where(s.products.c.id == product["id"]).values(name_snapshot=product["name_snapshot"]))
        invalid = deepcopy(self.bundle)
        invalid["dataset"]["manifest_json"]["counts"]["customers"] = 1
        with self.assertRaises(DatasetInvalid):
            self.store.publish(invalid)

    def test_05_other_seed_dataset_does_not_overwrite(self):
        before = self.store.inspect(self.bundle)
        other = generate(20261028)
        result = self.store.import_dataset(other)
        self.assertEqual("NEW", result["decision"])
        self.assertNotEqual(self.dataset_id, other["dataset"]["id"])
        self.assertEqual(before, self.store.inspect(self.bundle))
        self.assertEqual("PUBLISHED", self.store.publish(other)["status"])
        EVIDENCE["phase2_second_dataset"] = {"dataset_id": other["dataset"]["id"], "primary_unchanged": True,
                                           "status": self.store.inspect(other)["status"]}

    def test_06_import_failure_rolls_back_entire_dataset(self):
        other = generate(20261029)
        real_batch = storage._batch

        def fail_after_sales(conn, table, rows):
            real_batch(conn, table, rows)
            if table is s.sale_items:
                raise RuntimeError("injected failure after all sale items; rollback required")

        with patch.object(storage, "_batch", side_effect=fail_after_sales), self.assertRaises(RuntimeError):
            self.store.import_dataset(other)
        identifier = other["dataset"]["id"]
        self.assertEqual(0, self.query("SELECT COUNT(*) AS n FROM ingredient_poc_datasets WHERE id=%s", (identifier,))[0]["n"])
        self.assertEqual(0, self.query("SELECT COUNT(*) AS n FROM ingredient_poc_sale_items WHERE dataset_id=%s", (identifier,))[0]["n"])
        EVIDENCE["phase2_full_rollback"] = True

    def test_07_writer_has_no_ddl_or_v1_access_and_wrong_identity_fails(self):
        for sql in ("CREATE TABLE ingredient_poc_writer_must_not_create(id INT)", "SELECT COUNT(*) FROM interaction_events"):
            with self.subTest(sql=sql), self.assertRaises(DBAPIError) as failure:
                with self.store.engine.begin() as conn:
                    conn.execute(text(sql))
            self.assertEqual(1142, failure.exception.orig.args[0])
        wrong = storage.ResearchTarget(self.target.port, self.target.database, str(uuid4()), SHARED_UUID,
                                       self.target.username, self.target.password)
        with self.assertRaises(ValueError):
            wrong.connect()
        with self.assertRaises(ValueError):
            storage.ResearchTarget(self.target.port, "recommend_app", self.target.server_uuid, SHARED_UUID,
                                   self.target.username, self.target.password)
        self.assertEqual(self.v1_before, self.v1_counts())
        EVIDENCE["phase2_writer_least_privilege"] = True

    def test_08_concurrent_publish_is_atomic_and_reentrant(self):
        with ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(self.store.publish, self.bundle) for _ in range(2)]
            results = [f.result(timeout=90) for f in futures]
        self.assertEqual({"PUBLISHED", "UNCHANGED"}, {r["decision"] for r in results})
        self.assertTrue(all(r["status"] == "PUBLISHED" for r in results))
        checked = self.store.inspect(self.bundle)
        self.assertEqual("PUBLISHED", checked["status"])
        EVIDENCE["phase2_publish"] = {"status": checked["status"], "decisions": [r["decision"] for r in results],
                                     "table_checksums": checked["table_checksums"]}

    def test_09_published_immutable_and_reimport_still_noop(self):
        sale = self.bundle["tables"]["sale_items"][0]
        for sql, params in (("UPDATE ingredient_poc_datasets SET status='STAGED' WHERE id=:id", {"id": self.dataset_id}),
                            ("UPDATE ingredient_poc_sale_items SET quantity=2 WHERE id=:id", {"id": sale["id"]}),
                            ("UPDATE ingredient_poc_recipes SET recipe_version=99 WHERE dataset_id=:id", {"id": self.dataset_id})):
            with self.subTest(sql=sql), self.assertRaises(DBAPIError) as failure:
                with self.store.engine.begin() as conn:
                    conn.execute(text(sql), params)
            self.assertEqual(1644, failure.exception.orig.args[0])
        before = self.store.inspect(self.bundle)
        self.assertEqual("UNCHANGED", self.store.import_dataset(self.bundle)["decision"])
        self.assertEqual(before, self.store.inspect(self.bundle))
        self.assertEqual(self.v1_before, self.v1_counts())

    def test_10_temporal_sql_and_train_only_feature_access(self):
        split_rows = self.query("""SELECT CASE WHEN purchased_at < %s THEN 'TRAIN'
            WHEN purchased_at < %s THEN 'VALIDATION' ELSE 'TEST' END AS split,COUNT(*) AS n
            FROM ingredient_poc_sale_items WHERE dataset_id=%s GROUP BY split""", (T1.replace(tzinfo=None), T2.replace(tzinfo=None), self.dataset_id))
        self.assertEqual(self.validation["statistics"]["split_sale_counts"], {r["split"]: r["n"] for r in split_rows})
        crossing = self.query("""SELECT COUNT(*) AS n FROM (
            SELECT order_id,COUNT(DISTINCT CASE WHEN purchased_at < %s THEN 'TRAIN'
            WHEN purchased_at < %s THEN 'VALIDATION' ELSE 'TEST' END) AS n
            FROM ingredient_poc_sale_items WHERE dataset_id=%s GROUP BY order_id HAVING n>1
            ) AS crossing""", (T1.replace(tzinfo=None), T2.replace(tzinfo=None), self.dataset_id))
        self.assertEqual(0, crossing[0]["n"])
        cold = self.query("""SELECT COUNT(*) AS n FROM (
            SELECT customer_id FROM ingredient_poc_sale_items WHERE dataset_id=%s GROUP BY customer_id
            HAVING MIN(purchased_at)>=%s) AS cold""", (self.dataset_id, T1.replace(tzinfo=None)))
        self.assertEqual(20, cold[0]["n"])
        train = split_view(self.bundle)
        self.assertTrue(all(sale["purchased_at"] < T1 for sale in train["sale_items"]))
        EVIDENCE["phase2_temporal_sql"] = {"splits": split_rows, "crossing_orders": 0, "cold_customers": 20,
                                         "train_view_checksum": content_hash(train)}

    def test_11_export_replay_and_source_manifest_reproducibility(self):
        root = ARTIFACT_ROOT / "synthetic-dataset"
        payload = json.loads((root / "dataset.json").read_text())
        self.assertNotIn("evaluation", payload)
        payload["evaluation"] = json.loads((root / "evaluation.json").read_text())
        replay = loads(canonical_json(payload))
        self.assertEqual(canonical_json(self.bundle), canonical_json(replay))
        self.assertEqual(canonical_json(self.bundle), canonical_json(generate()))
        self.assertEqual(self.validation, validate(replay))

    def test_12_runtime_retry_rolls_back_full_transaction_on_lock_timeout(self):
        product = self.bundle["tables"]["products"][0]
        # A small STAGED probe with a different seed remains independent of published data.
        probe = generate(20261030)
        self.store.import_dataset(probe)
        product = probe["tables"]["products"][0]
        blocker = self.store.engine.connect()
        tx = blocker.begin()
        blocker.execute(select(s.datasets.c.id).where(s.datasets.c.id == probe["dataset"]["id"]).with_for_update())
        calls = []

        def operation(conn):
            calls.append(1)
            storage._locked_dataset(conn, probe["dataset"]["id"])
            # Rolled back on the first lock timeout; second fresh transaction sees committed state.
            row = conn.execute(select(s.products.c.name_snapshot).where(s.products.c.id == product["id"])).scalar_one()
            return row

        try:
            with ThreadPoolExecutor(max_workers=1) as pool:
                future = pool.submit(storage.retry_transaction, self.store.engine, operation)
                # Wait for real 1205 before releasing; second attempt signals through call count.
                import time
                deadline = time.monotonic() + 8
                while len(calls) < 2 and time.monotonic() < deadline:
                    time.sleep(0.05)
                self.assertGreaterEqual(len(calls), 2)
                tx.rollback()
                self.assertEqual(product["name_snapshot"], future.result(timeout=15))
        finally:
            if tx.is_active:
                tx.rollback()
            blocker.close()
        self.assertEqual(2, len(calls))
        EVIDENCE["phase2_runtime_retry"] = {"forced_error": 1205, "attempts": len(calls), "whole_transaction_retry": True}
