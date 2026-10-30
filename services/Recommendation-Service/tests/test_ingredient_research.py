from copy import deepcopy
from datetime import timedelta
from decimal import Decimal
import json
import math
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import numpy as np

from app.ingredient_poc.contracts import content_hash
from app.ingredient_poc.synthetic import T1, T2, canonical_json, generate, write_artifacts
from app.ingredient_poc.recommendation.data import ResearchDataset, sha256
from app.ingredient_poc.recommendation.evaluation import demos, evaluate
from app.ingredient_poc.recommendation.features import interest
from app.ingredient_poc.recommendation.model import IngredientRecommender


class IngredientResearchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = TemporaryDirectory(prefix="hvtra-research-unit-")
        cls.directory = Path(cls.temp.name)
        bundle = generate()  # Phase 2 fixture authoring only; model never receives its sidecar.
        write_artifacts(bundle, cls.directory / "input")
        cls.path = cls.directory / "input/dataset.json"
        cls.sha = sha256(cls.path)
        cls.data = ResearchDataset.load(cls.path, cls.sha)
        cls.model = IngredientRecommender.fit(cls.data)
        cls.model_path = cls.directory / "ingredient-research-v1.json"
        cls.model_sha = cls.model.save(cls.model_path)
        cls.customers = {g: next(c for c in sorted(cls.data.customers) if cls.data.cohort(c, T2) == g)
                         for g in ("PRODUCT_ONLY", "BOM_ONLY", "MIXED")}
        cls.cold = next(c for c in sorted(cls.data.customers) if not cls.data.history(c, T1))

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_product_only_taste_and_real_product_to_bom(self):
        c = self.customers["PRODUCT_ONLY"]
        p = self.model.profile(c, T2)
        self.assertGreater(p.product_inferred_orders, 0)
        self.assertEqual(p.custom_synthetic_orders, 0)
        result = self.model.recommend(c, as_of=T2, mode="CUSTOM_BOM")
        self.assertEqual(result["returned_k"], 5)
        self.assertTrue(all(r["recommendation_type"] == "CUSTOM_BOM" and r["recipe_id"] in self.data.recipes for r in result["recommendations"]))
        self.assertIn("PRODUCT suy diễn", result["recommendations"][0]["reason"])

    def test_bom_only_taste_and_real_bom_to_product(self):
        c = self.customers["BOM_ONLY"]
        p = self.model.profile(c, T2)
        self.assertEqual(p.product_inferred_orders, 0)
        self.assertGreater(p.custom_synthetic_orders, 0)
        result = self.model.recommend(c, as_of=T2, mode="PRODUCT")
        self.assertEqual(result["returned_k"], 5)
        self.assertTrue(all(r["item_id"] in self.data.products and r["variant_id"] in self.data.variants for r in result["recommendations"]))

    def test_mixed_one_profile_and_unified_global_ranking(self):
        c = self.customers["MIXED"]
        profile = self.model.profile(c, T2)
        self.assertGreater(profile.product_inferred_orders, 0)
        self.assertGreater(profile.custom_synthetic_orders, 0)
        result = self.model.recommend(c, as_of=T2)
        rows = result["recommendations"]
        self.assertEqual(len(rows), 5)
        self.assertEqual({r["customer_id"] for r in rows}, {c})
        self.assertEqual(len({r["recipe_hash"] for r in rows}), 5)
        self.assertEqual([r["rank"] for r in rows], list(range(1, 6)))
        self.assertEqual([r["score"] for r in rows], sorted([r["score"] for r in rows], reverse=True))

    def test_cold_popularity_fallback_finite_top5(self):
        r = self.model.recommend(self.cold, as_of=T1)
        self.assertEqual(r["fallback"], "POPULARITY_COLD")
        self.assertEqual(r["returned_k"], 5)
        self.assertTrue(all(math.isfinite(x["score"]) and 0 <= x["score"] <= 1 for x in r["recommendations"]))
        self.assertTrue(all(x["strategy"] == "POPULARITY_COLD" for x in r["recommendations"]))

    def test_same_ingredient_sets_different_quantities_and_magnitude(self):
        family = self.data.recipes[next(iter(self.data.recipes))]["recipe_family_key"]
        recipes = sorted([r for r in self.data.recipes.values() if r["recipe_family_key"] == family], key=lambda r: r["recipe_version"])
        a = self.model.features.vector(recipes[1]["id"])
        b = self.model.features.vector(recipes[3]["id"])
        self.assertFalse(np.array_equal(a, b))
        self.assertLess(math.exp(-0.5 * float((a - b) @ (a - b))), 1.0)
        # Magnitude survives even a uniform dry-ingredient multiplier.
        cloned = deepcopy(self.model)
        for i in cloned.data.items[recipes[1]["id"]]:
            if i["ingredient_role"] != "DILUENT":
                i["quantity_canonical"] *= 2
        self.assertFalse(np.allclose(a, cloned.features.vector(recipes[1]["id"])))

    def test_canonical_scales_water_and_waste(self):
        cloned = deepcopy(self.model)
        rid = next(r for r in self.model.recipe_ids if any(i["unit_symbol_snapshot"] == "kg" for i in self.data.items[r]))
        baseline = self.model.features.vector(rid)
        for i in cloned.data.items[rid]:
            i["waste_rate"] = Decimal(100)
            if i["ingredient_role"] == "DILUENT":
                i["quantity_canonical"] = Decimal(999999)
            elif i["unit_symbol_snapshot"] == "kg":
                i["unit_symbol_snapshot"] = "g"
                i["quantity_original"] = i["quantity_canonical"]
                i["canonical_factor"] = Decimal(1)
        np.testing.assert_array_equal(baseline, cloned.features.vector(rid))

    def test_recipe_cf_is_real_cosine_with_support_shrinkage(self):
        self.assertEqual(self.model.metadata["train_counts"], {"sales": 2660, "customers": 380, "recipes": 76})
        self.assertGreater(int((self.model.similarity > 0).sum()), 0)
        i, j = np.argwhere(self.model.similarity > 0)[0]
        a, b = [], []
        for c in sorted(self.data.customers):
            weights = interest(self.data.history(c, T1))
            a.append(weights.get(self.model.recipe_ids[i], 0))
            b.append(weights.get(self.model.recipe_ids[j], 0))
        a, b = np.asarray(a), np.asarray(b)
        shared = int(((a > 0) & (b > 0)).sum())
        expected = float(a @ b) / (np.linalg.norm(a) * np.linalg.norm(b)) * shared / (shared + 5)
        self.assertAlmostEqual(float(self.model.similarity[i, j]), expected, places=12)
        self.assertTrue(np.array_equal(np.diag(self.model.similarity), np.zeros(76)))

    def test_cf_missing_support_falls_back_to_content(self):
        cloned = deepcopy(self.model)
        cloned.similarity[:] = 0
        result = cloned.recommend(self.customers["MIXED"], as_of=T2)
        self.assertEqual(result["fallback"], "CB_FALLBACK")
        self.assertEqual(result["returned_k"], 5)
        self.assertTrue(all(r["strategy"] == "CB_FALLBACK" for r in result["recommendations"]))

    def test_discovery_excludes_seen_content_repeat_keeps_candidates(self):
        c = self.customers["MIXED"]
        seen = self.model.profile(c, T2).seen_hashes
        discovery = self.model.recommend(c, as_of=T2, policy="DISCOVERY", top_k=50)
        repeat = self.model.recommend(c, as_of=T2, policy="REPEAT", top_k=50)
        self.assertFalse({r["recipe_hash"] for r in discovery["recommendations"]} & seen)
        self.assertTrue({r["recipe_hash"] for r in repeat["recommendations"]} & seen)

    def test_deterministic_fit_scoring_and_artifact_reload(self):
        second = IngredientRecommender.fit(self.data)
        self.assertEqual(canonical_json(second.payload()), canonical_json(self.model.payload()))
        loaded = IngredientRecommender.load(self.model_path, self.model_sha, self.data)
        c = self.customers["MIXED"]
        self.assertEqual(self.model.recommend(c, as_of=T2), loaded.recommend(c, as_of=T2))

    def test_future_purchases_and_feedback_cannot_change_train_fit_or_profile(self):
        altered = deepcopy(self.data)
        for sale in altered.tables["sale_items"]:
            if sale["purchased_at"] >= T1:
                sale["quantity"] = Decimal(999)
        for rows in altered.feedback.values():
            for f in rows:
                if f["feedback_time"] >= T1:
                    f["feedback_type"] = "PREFERENCE_DISLIKE"
        other = IngredientRecommender.fit(altered)
        np.testing.assert_array_equal(self.model.similarity, other.similarity)
        self.assertEqual(self.model.popularity, other.popularity)
        self.assertEqual(self.model.metadata["fit_data_hash"], other.metadata["fit_data_hash"])
        c = self.customers["MIXED"]
        self.assertEqual(self.model.recommend(c, as_of=T1), other.recommend(c, as_of=T1))

    def test_future_recipes_not_candidates_and_wrong_asof_rejected(self):
        cloned = deepcopy(self.model)
        rid = next(r for r in self.data.recipes if r not in self.model.recipe_index)
        cloned.data.recipes[rid]["provenance"]["available_at"] = (T2 + timedelta(days=2)).isoformat()
        result = cloned.recommend(self.cold, as_of=T1, mode="CUSTOM_BOM", top_k=50)
        self.assertNotIn(rid, {r["item_id"] for r in result["recommendations"]})
        with self.assertRaises(ValueError):
            self.model.recommend(self.cold, as_of=T1 - timedelta(seconds=1))
        with self.assertRaises(ValueError):
            self.model.recommend(self.cold, as_of="2026-04-01")

    def test_future_recipe_quantities_do_not_fit_training_features_or_cf(self):
        altered = deepcopy(self.data)
        for rid, rows in altered.items.items():
            if rid not in self.model.recipe_index:
                for item in rows:
                    item["quantity_canonical"] *= Decimal(99)
        model = IngredientRecommender.fit(altered)
        self.assertEqual(self.model.metadata["fit_data_hash"], model.metadata["fit_data_hash"])
        self.assertEqual(self.model.features.columns, model.features.columns)
        np.testing.assert_array_equal(self.model.similarity, model.similarity)

    def test_dislike_reduces_preference_operational_and_future_do_not(self):
        c = self.customers["MIXED"]
        base = deepcopy(self.model)
        base.data.feedback[c] = []
        baseline = base.recommend(c, as_of=T2, mode="CUSTOM_BOM", policy="REPEAT", top_k=50)
        ranks = {r["recipe_id"]: r for r in baseline["recommendations"]}
        sale = next(s for s in self.data.history(c, T2) if s["recipe_id"] in ranks)
        feedback = {"sale_item_id": sale["id"], "feedback_type": "OPERATIONAL_RETURN", "feedback_time": T2 - timedelta(minutes=1)}
        operational = deepcopy(base)
        operational.data.feedback[c] = [feedback]
        self.assertEqual(baseline, operational.recommend(c, as_of=T2, mode="CUSTOM_BOM", policy="REPEAT", top_k=50))
        future = deepcopy(base)
        future.data.feedback[c] = [{**feedback, "feedback_type": "PREFERENCE_DISLIKE", "feedback_time": T2 + timedelta(seconds=1)}]
        self.assertEqual(baseline, future.recommend(c, as_of=T2, mode="CUSTOM_BOM", policy="REPEAT", top_k=50))
        negative = deepcopy(base)
        negative.data.feedback[c] = [{**feedback, "feedback_type": "PREFERENCE_DISLIKE"}]
        result = negative.recommend(c, as_of=T2, mode="CUSTOM_BOM", policy="REPEAT", top_k=50)
        changed = {r["recipe_id"]: r for r in result["recommendations"]}
        rid = sale["recipe_id"]
        if rid in changed:
            self.assertAlmostEqual(changed[rid]["score"], ranks[rid]["score"] * 0.5, places=9)
        self.assertIn(rid, negative.profile(c, T2).dislikes)

    def test_history_weights_bounded_and_one_order_not_double_counted(self):
        history = deepcopy(self.data.history(self.customers["MIXED"], T2))
        first = history[0]
        weights = interest([first])
        duplicate = {**first, "purchase_type": "CUSTOM_BOM", "quantity": Decimal(99999)}
        capped = interest([first, duplicate] * 100)
        self.assertLessEqual(capped[first["recipe_id"]], math.log1p(3))
        self.assertGreater(weights[first["recipe_id"]], 0)
        many_orders = [{**first, "order_id": str(n), "quantity": Decimal(99999)} for n in range(500)]
        self.assertEqual(interest(many_orders)[first["recipe_id"]], 3)

    def test_loader_does_not_open_latent_sidecar(self):
        original = Path.read_bytes
        original_text = Path.read_text

        def guarded_bytes(path, *args, **kwargs):
            self.assertNotEqual(path.name, "evaluation.json")
            return original(path, *args, **kwargs)

        def guarded_text(path, *args, **kwargs):
            self.assertNotEqual(path.name, "evaluation.json")
            return original_text(path, *args, **kwargs)

        with patch.object(Path, "read_bytes", guarded_bytes), patch.object(Path, "read_text", guarded_text):
            d = ResearchDataset.load(self.path, self.sha)
            self.assertEqual(self.data.id, d.id)

    def test_wrong_dataset_hash_source_and_model_guard(self):
        with self.assertRaises(ValueError):
            ResearchDataset.load(self.path, "f" * 64)
        with self.assertRaises(ValueError):
            IngredientRecommender.load(self.model_path, "f" * 64, self.data)
        changed = deepcopy(self.data)
        changed.id = "wrong-dataset"
        with self.assertRaises(ValueError):
            IngredientRecommender.load(self.model_path, self.model_sha, changed)
        with TemporaryDirectory(prefix="hvtra-bad-source-") as parent:
            parent = Path(parent)
            for name in ("manifest.json", "validation.json", "artifact-checksums.json"):
                (parent / name).write_bytes((self.path.parent / name).read_bytes())
            raw = json.loads(self.path.read_text())
            raw["dataset"]["source_kind"] = "ACTUAL"
            (parent / "dataset.json").write_text(canonical_json(raw))
            checks = json.loads((parent / "artifact-checksums.json").read_text())
            checks["dataset.json"] = sha256(parent / "dataset.json")
            (parent / "artifact-checksums.json").write_text(json.dumps(checks))
            with self.assertRaises(ValueError):
                ResearchDataset.load(parent / "dataset.json", checks["dataset.json"])

    def test_output_and_serving_artifact_guards(self):
        with self.assertRaises(ValueError):
            self.model.save(self.directory / "v1_hybrid_model.npz")
        with self.assertRaises(FileExistsError):
            self.model.save(self.model_path)
        for args in ({"mode": "INVALID"}, {"policy": "INVALID"}, {"top_k": 0}):
            with self.subTest(args=args), self.assertRaises(ValueError):
                self.model.recommend(self.cold, as_of=T1, **args)

    def test_cli_runs_real_inference_and_returns_bom_quantities(self):
        script = Path(__file__).resolve().parents[1] / "scripts/run_ingredient_research.py"
        result = subprocess.run([sys.executable, "-B", str(script), "infer", "--dataset", str(self.path),
                                 "--dataset-sha256", self.sha, "--model", str(self.model_path),
                                 "--model-sha256", self.model_sha, "--customer", self.customers["PRODUCT_ONLY"],
                                 "--mode", "CUSTOM_BOM", "--as-of", T2.isoformat()], capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        output = json.loads(result.stdout)
        self.assertEqual(output["returned_k"], 5)
        self.assertTrue(output["recommendations"][0]["ingredient_summary"])

    def test_demo_selection_and_evaluation_protocol_sanity(self):
        result = demos(self.model)
        self.assertEqual(set(result), {"PRODUCT_ONLY", "BOM_ONLY", "MIXED", "COLD_START"})
        self.assertTrue(all(p["returned_k"] == 5 for p in result.values()))
        metrics, predictions = evaluate(self.model, "VALIDATION")
        self.assertEqual(metrics["overall"]["full_top5_rate"], 1)
        self.assertEqual(metrics["excluded_unavailable_label_rows"], 0)
        self.assertGreater(metrics["overall"]["product_to_bom_customers"], 0)
        self.assertGreater(metrics["overall"]["bom_to_product_customers"], 0)
        for name in ("precision_at_5", "recall_at_5", "ndcg_at_5", "coverage_at_5"):
            self.assertTrue(0 <= metrics["overall"][name] <= 1)
        self.assertTrue(all(p["as_of"] == T1.isoformat() for p in predictions["predictions"]))
