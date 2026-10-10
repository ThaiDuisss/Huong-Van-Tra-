from copy import deepcopy
from dataclasses import replace
import json
from math import isfinite, log2
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

from offline_benchmark.common.manifest import build_manifest, WORKSPACE
from offline_benchmark.common.types import PredictionReport
from offline_benchmark.data.db_audit import read_table_counts
from offline_benchmark.metrics.evaluator import evaluate_payload, evaluate_reports, MODEL_NAMES
from offline_benchmark.metrics.ranking_metrics import ranking_metrics
from offline_benchmark.metrics.reporting import build_summary
from offline_benchmark.runners.evaluate_outputs import evaluate_existing
from offline_benchmark.split.chronological_split import chronological_split
from offline_benchmark.tests.test_benchmark import fixture


def payload(model_key="popularity"):
    snapshot = fixture()
    splits = chronological_split(snapshot.purchases)
    return {
        "split": build_manifest(snapshot, splits),
        "predictions": [
            {"model": MODEL_NAMES[model_key], "customer_id": split.customer_id,
             "top5": ["p0", "p1", "p2", "p3", "p4"],
             "test": [event.product_id for event in split.test],
             "hits": [product for product in ("p0", "p1", "p2", "p3", "p4")
                      if product in {event.product_id for event in split.test}]}
            for split in splits
        ],
    }


class RankingMetricTests(unittest.TestCase):
    def test_hand_calculated_precision_recall_ndcg_ap(self):
        result = ranking_metrics(tuple("ABCDE"), tuple("BDX"))
        self.assertEqual(result.precision_at_5, 2 / 5)
        self.assertEqual(result.recall_at_5, 2 / 3)
        dcg = 1 / log2(3) + 1 / log2(5)
        idcg = 1 + 1 / log2(3) + 1 / log2(4)
        self.assertAlmostEqual(result.ndcg_at_5, dcg / idcg, places=14)
        self.assertAlmostEqual(result.ap_at_5, (1 / 2 + 2 / 4) / 3, places=14)

    def test_duplicate_test_is_product_level(self):
        repeated = ranking_metrics(tuple("ABCDE"), ("A", "A", "B"))
        unique = ranking_metrics(tuple("ABCDE"), ("A", "B"))
        self.assertEqual(repeated, unique)
        self.assertEqual(repeated.precision_at_5, 2 / 5)
        self.assertEqual(repeated.recall_at_5, 1)
        self.assertEqual(repeated.ndcg_at_5, 1)
        self.assertEqual(repeated.ap_at_5, 1)

    def test_zero_hits(self):
        result = ranking_metrics(tuple("ABCDE"), tuple("XYZ"))
        self.assertEqual((result.precision_at_5, result.recall_at_5, result.ndcg_at_5, result.ap_at_5), (0, 0, 0, 0))

    def test_perfect_top5(self):
        result = ranking_metrics(tuple("ABCDE"), tuple("ABCDE"))
        self.assertEqual((result.precision_at_5, result.recall_at_5, result.ndcg_at_5, result.ap_at_5), (1, 1, 1, 1))

    def test_short_top5_keeps_fixed_precision_denominator(self):
        result = ranking_metrics(("A", "B"), ("A", "B"))
        self.assertEqual(result.precision_at_5, 0.4)
        self.assertEqual(result.recall_at_5, 1)
        self.assertEqual(result.ndcg_at_5, 1)
        self.assertEqual(result.ap_at_5, 1)

    def test_more_than_five_relevant_items_caps_ap_and_idcg_only(self):
        result = ranking_metrics(tuple("ABCDE"), tuple("ABCDEFG"))
        self.assertEqual(result.precision_at_5, 1)
        self.assertEqual(result.recall_at_5, 5 / 7)
        self.assertEqual(result.ndcg_at_5, 1)
        self.assertEqual(result.ap_at_5, 1)

    def test_empty_recommendations_are_zero(self):
        result = ranking_metrics((), ("A",))
        self.assertEqual((result.precision_at_5, result.recall_at_5, result.ndcg_at_5, result.ap_at_5), (0, 0, 0, 0))

    def test_empty_test_is_an_explicit_error(self):
        with self.assertRaisesRegex(ValueError, "undefined"):
            ranking_metrics(("A",), ())

    def test_duplicate_long_or_blank_recommendations_rejected(self):
        for top5, test in ((("A", "A"), ("A",)), (tuple("ABCDEF"), ("A",)),
                           (("",), ("A",)), (("A",), (" ",))):
            with self.subTest(top5=top5, test=test), self.assertRaises(ValueError):
                ranking_metrics(top5, test)

    def test_rank_order_affects_ranking_metrics_only(self):
        early = ranking_metrics(tuple("ABCDE"), ("A",))
        late = ranking_metrics(tuple("BCDEA"), ("A",))
        self.assertEqual(early.precision_at_5, late.precision_at_5)
        self.assertEqual(early.recall_at_5, late.recall_at_5)
        self.assertGreater(early.ndcg_at_5, late.ndcg_at_5)
        self.assertGreater(early.ap_at_5, late.ap_at_5)


class AggregateTests(unittest.TestCase):
    def test_macro_averages_map_and_dynamic_coverage(self):
        reports = (
            PredictionReport("Example", "one", ("A", "B", "C", "D", "E"), ("A",), ("A",)),
            PredictionReport("Example", "two", ("B", "D", "E", "F", "G"), ("B", "X", "Y"), ("B",)),
        )
        result = evaluate_reports(reports, tuple("ABCDEFGH"))
        a = result["aggregate"]
        self.assertEqual(a["evaluated_customers"], 2)
        self.assertEqual(a["total_top5_recommendation_slots"], 10)
        self.assertEqual(a["total_hits"], 2)
        self.assertEqual(a["mean_hits_per_customer"], 1)
        self.assertEqual(a["precision_at_5"], 0.2)
        self.assertEqual(a["recall_at_5"], (1 + 1 / 3) / 2)
        self.assertNotEqual(a["recall_at_5"], 2 / 4)
        self.assertEqual(a["map_at_5"], (1 + 1 / 3) / 2)
        self.assertEqual(a["unique_recommended_products"], 7)
        self.assertEqual(a["recommendable_catalog_products"], 8)
        self.assertEqual(a["catalog_coverage_at_5"], 7 / 8)
        changed_catalog = evaluate_reports(reports, tuple("ABCDEFGHIJ"))
        self.assertEqual(changed_catalog["aggregate"]["catalog_coverage_at_5"], 7 / 10)

    def test_exact_hits_and_candidate_catalog_enforced(self):
        report = PredictionReport("Example", "one", ("A", "B"), ("A", "A"), ("A",))
        for invalid, catalog in ((replace(report, hits=("A", "A")), tuple("ABC")),
                                 (replace(report, hits=()), tuple("ABC")), (report, ("A",))):
            with self.assertRaises(ValueError):
                evaluate_reports((invalid,), catalog)

    def test_invalid_catalog_empty_or_duplicate_customers_and_mixed_models(self):
        report = PredictionReport("Example", "one", ("A",), ("A",), ("A",))
        cases = (((report,), ()), ((report,), ("A", "A")), ((), ("A",)),
                 ((report, report), ("A",)), ((report, replace(report, customer_id="two", model="Other")), ("A",)))
        for reports, catalog in cases:
            with self.assertRaises(ValueError):
                evaluate_reports(reports, catalog)

    def test_finite_bounded_and_deterministic_metrics(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        result = evaluate_payload("popularity", payload(), snapshot, splits)
        repeat = evaluate_payload("popularity", payload(), snapshot, splits)
        self.assertEqual(result, repeat)
        self.assertEqual(result, json.loads(json.dumps(result, allow_nan=False)))
        for row in result["customers"]:
            for key in ("precision_at_5", "recall_at_5", "ndcg_at_5", "ap_at_5"):
                self.assertTrue(isfinite(row[key]) and 0 <= row[key] <= 1)
        for key in ("precision_at_5", "recall_at_5", "ndcg_at_5", "map_at_5", "catalog_coverage_at_5"):
            self.assertTrue(isfinite(result["aggregate"][key]) and 0 <= result["aggregate"][key] <= 1)


class OutputValidationTests(unittest.TestCase):
    def test_stale_manifest_wrong_test_partial_or_duplicate_cohort_rejected(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        modifications = []
        altered = payload()
        altered["split"]["split_sha256"] = "wrong"
        modifications.append(altered)
        altered = payload()
        altered["predictions"][0]["test"] = ["future"]
        modifications.append(altered)
        altered = payload()
        altered["predictions"].pop()
        modifications.append(altered)
        altered = payload()
        altered["predictions"].append(altered["predictions"][0])
        modifications.append(altered)
        altered = payload()
        altered["predictions"][0]["model"] = "Other"
        modifications.append(altered)
        for altered in modifications:
            with self.assertRaises(ValueError):
                evaluate_payload("popularity", altered, snapshot, splits)

    def test_summary_requires_all_six_and_common_test_customer_catalog(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        results = tuple(evaluate_payload(key, payload(key), snapshot, splits) for key in MODEL_NAMES)
        self.assertEqual(len(build_summary(results)["models"]), 6)
        with self.assertRaises(ValueError):
            build_summary(results[:1])
        for field in ("test_sha256", "customer_set_sha256", "catalog_product_ids", "split"):
            altered = deepcopy(results)
            altered[-1][field] = "wrong"
            with self.assertRaises(ValueError):
                build_summary(altered)

    def test_selected_customer_does_not_shrink_aggregates_no_fit_and_inputs_unchanged(self):
        snapshot = fixture()
        with TemporaryDirectory(dir=WORKSPACE) as directory:
            root = Path(directory)
            for key in MODEL_NAMES:
                (root / f"{key}.json").write_text(json.dumps(payload(key)), encoding="utf-8")
            before = {path.name: path.read_bytes() for path in root.glob("*.json")}
            counts = {"products": len(snapshot.products)}
            with patch("offline_benchmark.runners.evaluate_outputs.read_table_counts", return_value=counts), \
                 patch("offline_benchmark.runners.evaluate_outputs.load_snapshot", return_value=snapshot), \
                 patch("offline_benchmark.runners.evaluate_outputs.require_manifest"), \
                 patch("offline_benchmark.runners.evaluate_outputs.artifact_path", side_effect=lambda name: root / name), \
                 patch("offline_benchmark.runners.common.evaluate", side_effect=AssertionError("Model fitting forbidden")):
                result = evaluate_existing(None, tuple(MODEL_NAMES), "alice")
            self.assertEqual(result[0]["aggregate"]["evaluated_customers"], 2)
            self.assertEqual(len(json.loads((root / "benchmark_summary.json").read_text())["models"]), 6)
            self.assertTrue(json.loads((root / "evaluation_db_audit.json").read_text())["unchanged"])
            self.assertEqual(before, {name: (root / name).read_bytes() for name in before})

    def test_changed_row_counts_prevent_publishing_metrics(self):
        snapshot = fixture()
        with TemporaryDirectory(dir=WORKSPACE) as directory:
            root = Path(directory)
            (root / "popularity.json").write_text(json.dumps(payload()), encoding="utf-8")
            with patch("offline_benchmark.runners.evaluate_outputs.read_table_counts", side_effect=[{"products": 8}, {"products": 9}]), \
                 patch("offline_benchmark.runners.evaluate_outputs.load_snapshot", return_value=snapshot), \
                 patch("offline_benchmark.runners.evaluate_outputs.require_manifest"), \
                 patch("offline_benchmark.runners.evaluate_outputs.artifact_path", side_effect=lambda name: root / name):
                with self.assertRaisesRegex(ValueError, "row counts changed"):
                    evaluate_existing(None, ("popularity",))
            self.assertFalse((root / "popularity_metrics.json").exists())

    def test_changed_history_prevents_publishing_metrics(self):
        snapshot = fixture()
        changed = replace(snapshot, products=snapshot.products[:-1])
        with TemporaryDirectory(dir=WORKSPACE) as directory:
            root = Path(directory)
            (root / "popularity.json").write_text(json.dumps(payload()), encoding="utf-8")
            with patch("offline_benchmark.runners.evaluate_outputs.read_table_counts", return_value={"products": 8}), \
                 patch("offline_benchmark.runners.evaluate_outputs.load_snapshot", side_effect=[snapshot, changed]), \
                 patch("offline_benchmark.runners.evaluate_outputs.require_manifest"), \
                 patch("offline_benchmark.runners.evaluate_outputs.artifact_path", side_effect=lambda name: root / name):
                with self.assertRaisesRegex(ValueError, "history/catalog changed"):
                    evaluate_existing(None, ("popularity",))
            self.assertFalse((root / "popularity_metrics.json").exists())

    def test_db_count_reader_only_selects_rolls_back_and_disposes(self):
        engine = MagicMock()
        connection = engine.connect.return_value.__enter__.return_value
        connection.scalar.return_value = 7
        with patch("app.core.settings.Settings", return_value=SimpleNamespace(sqlalchemy_database_url="unused")), \
             patch("offline_benchmark.data.db_audit.create_engine", return_value=engine):
            counts = read_table_counts()
        self.assertEqual(len(counts), 7)
        self.assertTrue(all(call.args[0].is_select for call in connection.scalar.call_args_list))
        self.assertIn("SET TRANSACTION READ ONLY", [call.args[0] for call in connection.exec_driver_sql.call_args_list])
        connection.rollback.assert_called_once()
        connection.commit.assert_not_called()
        engine.dispose.assert_called_once()


if __name__ == "__main__":
    unittest.main()
