from contextlib import redirect_stdout
from dataclasses import replace
from datetime import datetime, timedelta
from importlib import import_module
import io
import json
from pathlib import Path
import runpy
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

from sqlalchemy.dialects import mysql

from offline_benchmark.common.filtering import matching_hits, top_five
from offline_benchmark.common.manifest import artifact_path, build_manifest, require_manifest, WORKSPACE
from offline_benchmark.common.reporting import serialize_reports
from offline_benchmark.common.types import CatalogProduct, DatabaseSnapshot, PurchaseEvent, TrainingInput
from offline_benchmark.data.db_loader import load_snapshot, read_snapshot, relevant_ingredients
from offline_benchmark.models.popularity import Popularity
from offline_benchmark.runners.common import evaluate, MODELS
from offline_benchmark.split.chronological_split import chronological_split, verify_split


def fixture():
    products = tuple(
        CatalogProduct(f"p{i}", "tea" if i < 4 else "fruit", ("leaf" if i < 4 else "berry",))
        for i in range(8)
    )
    purchases = tuple(
        PurchaseEvent(f"{user}-{i:02}", user, f"p{(i % 4) + offset}",
                      datetime(2026, 1, 1) + timedelta(days=i), f"{user}-{i:02}", "PURCHASE")
        for user, offset in (("alice", 0), ("bob", 4)) for i in range(10)
    )
    return DatabaseSnapshot(purchases, products)


class SplitTests(unittest.TestCase):
    def test_chronological_80_20_and_eligibility(self):
        snapshot = fixture()
        short = tuple(replace(e, event_id=f"short-{i}", order_id=f"short-{i}", customer_id="short")
                      for i, e in enumerate(snapshot.purchases[:4]))
        blank = replace(snapshot.purchases[0], event_id="blank", customer_id=" ")
        anonymous = replace(blank, event_id="anonymous", customer_id=None)
        splits = chronological_split(tuple(reversed(snapshot.purchases + short + (blank, anonymous))))
        self.assertEqual([s.customer_id for s in splits], ["alice", "bob"])
        for split in splits:
            self.assertEqual(len(split.train), 8)
            self.assertEqual(len(split.test), 2)
            self.assertEqual([e.event_id for e in split.train], [f"{split.customer_id}-{i:02}" for i in range(8)])
            self.assertTrue(split.train[-1].event_time < split.test[0].event_time)

    def test_minimum_five_and_floor_rounding(self):
        events = fixture().purchases[:7]
        for size in (5, 6, 7):
            split = chronological_split(events[:size])[0]
            self.assertEqual(len(split.train), 4 * size // 5)
            self.assertEqual(len(split.test), size - 4 * size // 5)

    def test_equal_timestamps_use_stable_real_order_id(self):
        events = tuple(replace(e, event_time=datetime(2026, 1, 1)) for e in fixture().purchases)
        forward = chronological_split(events)
        self.assertEqual(forward, chronological_split(tuple(reversed(events))))
        self.assertEqual(forward[0].test[0].event_id, "alice-08")

    def test_duplicate_or_missing_historical_identifier_stops(self):
        events = fixture().purchases
        for invalid in (events + (events[0],), (replace(events[0], event_id=""),) + events[1:],
                        (replace(events[0], event_time=None),) + events[1:]):
            with self.assertRaises(ValueError):
                chronological_split(invalid)

    def test_no_leakage_and_corrupt_split_rejected(self):
        splits = chronological_split(fixture().purchases)
        train_ids = {e.event_id for s in splits for e in s.train}
        test_ids = {e.event_id for s in splits for e in s.test}
        self.assertFalse(train_ids & test_ids)
        corrupt = replace(splits[0], test=(splits[0].train[-1],) + splits[0].test[1:])
        with self.assertRaises(ValueError):
            verify_split((corrupt,) + splits[1:])


class ModelTests(unittest.TestCase):
    def test_popularity_counts_train_events_only_including_repeats(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        train = tuple(e for s in splits for e in s.train)
        model = Popularity(TrainingInput(("alice", "bob"), snapshot.products, train))
        self.assertEqual(model.scores("alice")["p0"], 2)
        self.assertEqual(model.scores("alice"), model.scores("bob"))
        self.assertEqual(top_five(model.scores("alice")), ("p0", "p1", "p2", "p3", "p4"))
        # Full history would give p0/p1 three purchases; quantity never enters the contract.
        self.assertEqual(sum(model.counts.values()), 16)

    def test_top5_order_and_nonfinite_scores(self):
        self.assertEqual(top_five({"p3": 2, "p1": 2, "p0": 0, "p2": 9}), ("p2", "p1", "p3", "p0"))
        with self.assertRaises(ValueError):
            top_five({"p0": float("nan")})

    def test_hits_unique_and_in_prediction_order(self):
        self.assertEqual(matching_hits(("p2", "p1", "p3"), ("p1", "p2", "p1")), ("p2", "p1"))
        self.assertEqual(matching_hits(("p0",), ("p1",)), ())

    def test_all_six_models_ignore_changes_to_test_products(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        changed = tuple(replace(s, test=tuple(replace(e, product_id="future-only") for e in s.test)) for s in splits)
        for key in MODELS:
            with self.subTest(model=key):
                before = evaluate(key, snapshot.products, splits)
                after = evaluate(key, snapshot.products, changed)
                self.assertEqual([r.top5 for r in before], [r.top5 for r in after])
                self.assertEqual(len(before[0].top5), 5)
                self.assertEqual(after[0].hits, ())

    def test_content_cf_svd_and_personalized_are_customer_specific(self):
        snapshot = fixture()
        for key in ("content_based", "item_item_cf", "svd", "personalized_hybrid"):
            with self.subTest(model=key):
                reports = evaluate(key, snapshot.products, chronological_split(snapshot.purchases))
                self.assertNotEqual(reports[0].top5, reports[1].top5)

    def test_cold_start_has_no_target_history_profile(self):
        snapshot = fixture()
        reports = evaluate("cold_start_hybrid", snapshot.products, chronological_split(snapshot.purchases))
        self.assertEqual(reports[0].top5, reports[1].top5)

    def test_customer_selection_does_not_change_training(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        for key in MODELS:
            with self.subTest(model=key):
                all_reports = evaluate(key, snapshot.products, splits)
                one_report = evaluate(key, snapshot.products, splits, "alice")
                self.assertEqual(all_reports[0], one_report[0])

    def test_no_metrics_in_serialized_output(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        parsed = json.loads(serialize_reports(evaluate("popularity", snapshot.products, splits), build_manifest(snapshot, splits)))
        self.assertEqual(set(parsed["predictions"][0]), {"model", "customer_id", "top5", "test", "hits"})


class DatabaseSafetyTests(unittest.TestCase):
    def test_entrypoints_dispatch_only_the_named_model(self):
        for key in MODELS:
            with self.subTest(model=key):
                with patch("offline_benchmark.runners.common.run") as runner:
                    runpy.run_path(str(WORKSPACE / "runners" / f"run_{key}.py"), run_name="__main__")
                runner.assert_called_once_with(key)

    def test_packaging_and_unknown_type_excluded_and_variants_deduplicated(self):
        rows = (("p0", "leaf", 1), ("p0", "leaf", 1), ("p0", "bag", 0),
                ("p0", "unknown", None), ("p1", "bag", 0))
        self.assertEqual(relevant_ingredients(rows), {"p0": ("leaf",)})

    def test_catalog_query_uses_type_one_and_history_never_uses_aggregates(self):
        db = MagicMock()
        db.execute.side_effect = [(), (("p0", "leaf", 1),)]
        products = (SimpleNamespace(product_id="p0", category_code="tea"),)
        with patch("offline_benchmark.data.db_loader.TrainingDataRepository.load_active_products", return_value=products):
            snapshot = read_snapshot(db)
        statements = [call.args[0] for call in db.execute.call_args_list]
        sql = [str(stmt.compile(dialect=mysql.dialect(), compile_kwargs={"literal_binds": True})) for stmt in statements]
        self.assertIn("event_time", sql[0])
        self.assertIn("interaction_events.id", sql[0])
        self.assertIn("interaction_events.order_id", sql[0])
        self.assertIn("interaction_events.event_type", sql[0])
        self.assertIn("'PURCHASE'", sql[0])
        self.assertIn("ingredients.type = 1", sql[1])
        self.assertIn("is_active_bom", sql[1])
        self.assertTrue(all(stmt.is_select for stmt in statements))
        self.assertTrue(all("customer_interactions" not in stmt for stmt in sql))
        self.assertEqual(snapshot.products[0].ingredient_codes, ("leaf",))
        db.commit.assert_not_called()
        db.flush.assert_not_called()
        db.add.assert_not_called()

    def test_loader_enforces_readonly_consistent_snapshot_and_rollback(self):
        engine = MagicMock()
        connection = engine.connect.return_value.__enter__.return_value
        config = SimpleNamespace(sqlalchemy_database_url="unused-test-url")
        with patch("app.core.settings.Settings", return_value=config), \
             patch("offline_benchmark.data.db_loader.create_engine", return_value=engine), \
             patch("offline_benchmark.data.db_loader.Session") as session, \
             patch("offline_benchmark.data.db_loader.read_snapshot", return_value=fixture()):
            load_snapshot()
        self.assertEqual([c.args[0] for c in connection.exec_driver_sql.call_args_list], [
            "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ",
            "SET TRANSACTION READ ONLY",
            "START TRANSACTION WITH CONSISTENT SNAPSHOT",
        ])
        session.assert_called_once_with(bind=connection, autoflush=False)
        connection.rollback.assert_called_once()
        connection.commit.assert_not_called()
        engine.dispose.assert_called_once()

    def test_loader_rolls_back_when_schema_read_fails(self):
        engine = MagicMock()
        connection = engine.connect.return_value.__enter__.return_value
        with patch("app.core.settings.Settings", return_value=SimpleNamespace(sqlalchemy_database_url="unused")), \
             patch("offline_benchmark.data.db_loader.create_engine", return_value=engine), \
             patch("offline_benchmark.data.db_loader.Session"), \
             patch("offline_benchmark.data.db_loader.read_snapshot", side_effect=RuntimeError("schema")):
            with self.assertRaises(RuntimeError):
                load_snapshot()
        connection.rollback.assert_called_once()
        engine.dispose.assert_called_once()

    def test_each_runner_loads_only_its_model_and_never_writes_db(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        manifest = build_manifest(snapshot, splits)
        with TemporaryDirectory(dir=WORKSPACE) as directory:
            for key in MODELS:
                with self.subTest(model=key):
                    module = import_module(f"offline_benchmark.runners.run_{key}")
                    path = Path(directory) / f"{key}.json"
                    args = SimpleNamespace(env_file=None, customer_id=None, output=None)
                    with patch("offline_benchmark.runners.common.arguments", return_value=args), \
                         patch("offline_benchmark.runners.common.load_snapshot", return_value=snapshot) as loader, \
                         patch("offline_benchmark.runners.common.require_manifest") as check, \
                         patch("offline_benchmark.runners.common.artifact_path", return_value=path), \
                         patch("offline_benchmark.runners.common.import_module", wraps=import_module) as factory, \
                         redirect_stdout(io.StringIO()):
                        module.run(key)
                    loader.assert_called_once_with(None)
                    check.assert_called_once_with(manifest)
                    factory.assert_called_once_with(f"offline_benchmark.models.{key}")
                    self.assertEqual(len(json.loads(path.read_text())["predictions"]), 2)


class ManifestTests(unittest.TestCase):
    def test_manifest_deterministic_and_catalog_history_drift_detected(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        manifest = build_manifest(snapshot, splits)
        reversed_snapshot = replace(snapshot, purchases=tuple(reversed(snapshot.purchases)))
        self.assertEqual(manifest, build_manifest(reversed_snapshot, chronological_split(reversed_snapshot.purchases)))
        with TemporaryDirectory(dir=WORKSPACE) as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with patch("offline_benchmark.common.manifest.MANIFEST_PATH", path):
                require_manifest(manifest)
                changed = replace(snapshot, products=(replace(snapshot.products[0], ingredient_codes=("new",)),) + snapshot.products[1:])
                with self.assertRaises(ValueError):
                    require_manifest(build_manifest(changed, splits))
                changed = replace(snapshot, purchases=snapshot.purchases + (replace(snapshot.purchases[-1], event_id="new"),))
                with self.assertRaises(ValueError):
                    require_manifest(build_manifest(changed, chronological_split(changed.purchases)))

    def test_output_cannot_escape_workspace(self):
        for filename in ("../outside.json", "C:/outside.json", "", ".."):
            with self.assertRaises(ValueError):
                artifact_path(filename)


if __name__ == "__main__":
    unittest.main()
