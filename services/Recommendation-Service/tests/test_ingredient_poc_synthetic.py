from copy import deepcopy
from datetime import timedelta
from decimal import Decimal
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from app.ingredient_poc.contracts import content_hash
from app.ingredient_poc.synthetic import (
    T1, canonical_json, generate, loads, recipe_contract, recipe_hash, sale_contract,
    seal, split_view, write_artifacts,
)
from app.ingredient_poc.synthetic_store import ResearchTarget, SyntheticStore
from app.ingredient_poc.synthetic_validation import DatasetInvalid, validate


class SyntheticDatasetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle = generate()
        cls.report = validate(cls.bundle)

    def test_same_seed_canonical_replay_and_typed_reload(self):
        self.assertEqual(canonical_json(self.bundle), canonical_json(generate()))
        self.assertEqual(canonical_json(self.bundle), canonical_json(loads(canonical_json(self.bundle))))

    def test_other_seed_has_separate_dataset_and_entity_ids(self):
        other = generate(20261028)
        self.assertEqual("PASS", validate(other)["status"])
        self.assertNotEqual(self.bundle["dataset"]["id"], other["dataset"]["id"])
        self.assertNotEqual(self.bundle["dataset"]["source_snapshot_hash"], other["dataset"]["source_snapshot_hash"])
        self.assertFalse({r["id"] for r in other["tables"]["customers"]}
                         & {r["id"] for r in self.bundle["tables"]["customers"]})

    def test_counts_cohorts_and_cross_domain_orders_from_history(self):
        stats = self.report["statistics"]
        self.assertEqual(4000, stats["counts"]["sale_items"])
        self.assertEqual({"PRODUCT_ONLY": 120, "BOM_ONLY": 120, "BOTH": 160}, stats["cohorts"])
        self.assertEqual(880, stats["mixed_orders"])
        self.assertGreater(stats["repeat_customer_recipe_edges"], 200)
        self.assertGreaterEqual(stats["train_family_pairs_shared_ge5"], 100)

    def test_quantities_and_unit_scale_distinguish_same_ingredient_sets(self):
        by_family = {}
        for r in self.bundle["tables"]["recipes"]:
            by_family.setdefault(r["recipe_family_key"], set()).add(r["recipe_hash"])
        self.assertTrue(all(len(hashes) == 4 for hashes in by_family.values()))
        self.assertTrue(any(i["unit_symbol_snapshot"] == "kg" for i in self.bundle["tables"]["recipe_items"]))
        self.assertTrue(any(i["unit_symbol_snapshot"] == "L" for i in self.bundle["tables"]["recipe_items"]))

    def test_future_changes_do_not_change_train_view(self):
        altered = deepcopy(self.bundle)
        for sale in altered["tables"]["sale_items"]:
            if sale["purchased_at"] >= T1:
                sale["quantity"] = Decimal(99)
        altered["evaluation"]["profiles"] = {}
        self.assertEqual(content_hash(split_view(self.bundle)), content_hash(split_view(altered)))
        self.assertEqual(76, len(split_view(self.bundle)["recipes"]))
        self.assertNotIn("evaluation", split_view(self.bundle))

    def assert_invalid(self, bundle, code, *, reseal=True):
        if reseal:
            seal(bundle)
        report = validate(bundle, raise_on_error=False)
        self.assertEqual("FAIL", report["status"])
        self.assertIn(code, {r["code"] for r in report["reasons"]})
        with self.assertRaises(DatasetInvalid):
            validate(bundle)

    def test_reject_quantity_unit_hash_and_unknown_precision(self):
        for column, value, code in (("quantity_original", Decimal(-1), "RECIPE_QUANTITY_UNIT"),
                                    ("quantity_original", Decimal("1.000000001"), "SCALAR_TYPE_PRECISION"),
                                    ("canonical_unit", "ml", "REFERENCE"),
                                    ("quantity_canonical", Decimal(999), "RECIPE_QUANTITY_UNIT")):
            with self.subTest(column=column, value=value):
                bad = deepcopy(self.bundle)
                bad["tables"]["recipe_items"][0][column] = value
                self.assert_invalid(bad, code)
        bad = deepcopy(self.bundle)
        bad["tables"]["recipes"][0]["recipe_hash"] = "f" * 64
        self.assert_invalid(bad, "RECIPE_HASH")

    def test_reject_empty_recipe(self):
        bad = deepcopy(self.bundle)
        rid = bad["tables"]["recipes"][0]["id"]
        bad["tables"]["recipe_items"] = [i for i in bad["tables"]["recipe_items"] if i["recipe_id"] != rid]
        self.assert_invalid(bad, "RECIPE_NONEMPTY")

    def test_reject_cross_dataset_customer_and_variant_conflicts(self):
        for kind in ("cross-dataset", "customer", "variant"):
            with self.subTest(kind=kind):
                bad = deepcopy(self.bundle)
                if kind == "cross-dataset":
                    bad["tables"]["recipe_items"][0]["dataset_id"] = "f" * 36
                else:
                    sale = next(r for r in bad["tables"]["sale_items"] if r["purchase_type"] == "PRODUCT")
                    sale["customer_id" if kind == "customer" else "variant_id"] = next(
                        r["id"] for r in bad["tables"]["customers" if kind == "customer" else "variants"]
                        if r["id"] != sale["customer_id" if kind == "customer" else "variant_id"])
                self.assert_invalid(bad, "CROSS_DATASET" if kind == "cross-dataset" else "REFERENCE")

    def test_reject_duplicate_business_line_with_new_event(self):
        bad = deepcopy(self.bundle)
        row = deepcopy(bad["tables"]["sale_items"][0])
        row.update(id="b4038b1a-4933-469e-9e57-143f052cc5a8", source_event_id="4ce40ee4-e197-4fb1-8908-f2693cb032a8")
        bad["tables"]["sale_items"].append(row)
        self.assert_invalid(bad, "DUPLICATE_BUSINESS_KEY")

    def test_reject_noncompleted_and_fake_product_for_custom(self):
        bad = deepcopy(self.bundle)
        bad["tables"]["orders"][0]["order_status"] = "PENDING"
        self.assert_invalid(bad, "REFERENCE")
        bad = deepcopy(self.bundle)
        sale = next(r for r in bad["tables"]["sale_items"] if r["purchase_type"] == "CUSTOM_BOM")
        sale["product_id"] = bad["tables"]["products"][0]["id"]
        self.assert_invalid(bad, "SALE_CONTRACT")

    def test_reject_future_recipe_and_split_order(self):
        bad = deepcopy(self.bundle)
        recipe = bad["tables"]["recipes"][0]
        recipe["provenance"]["available_at"] = "2027-01-01T00:00:00+00:00"
        # split_view also denies future recipe access, so validate must report rather than crash.
        self.assert_invalid(bad, "FUTURE_RECIPE")
        bad = deepcopy(self.bundle)
        orders = {}
        for sale in bad["tables"]["sale_items"]:
            orders.setdefault(sale["order_id"], []).append(sale)
        pair = next(rows for rows in orders.values() if len(rows) == 2 and rows[0]["purchased_at"] < T1)
        pair[1]["purchased_at"] = T1 + timedelta(days=1)
        pair[1]["payload_hash"] = sale_contract(pair[1]).payload_hash()
        self.assert_invalid(bad, "ORDER_SPLIT")

    def test_reject_manifest_tamper_and_latent_feature_leak(self):
        bad = deepcopy(self.bundle)
        bad["dataset"]["manifest_json"]["counts"]["customers"] = 999
        self.assert_invalid(bad, "MANIFEST_COUNTS_CHECKSUM", reseal=False)
        bad = deepcopy(self.bundle)
        bad["tables"]["products"][0]["provenance"]["weights"] = [1, 2]
        self.assert_invalid(bad, "LATENT_FEATURE_LEAK")

    def test_no_source_ids_and_no_fake_actual_snapshots(self):
        bad = deepcopy(self.bundle)
        bad["tables"]["variants"][0]["source_variant_id"] = "3cbbf1bb-f2ac-409a-aaef-ae0960643cd7"
        self.assert_invalid(bad, "SYNTHETIC_SOURCE_ID")

    def test_reject_role_dimension_mismatch_even_with_recomputed_hash(self):
        bad = deepcopy(self.bundle)
        item = next(i for i in bad["tables"]["recipe_items"] if i["ingredient_role"] == "BASE_TEA")
        count = next(u for u in bad["tables"]["units"] if u["symbol"] == "count")
        item.update(unit_id=count["id"], unit_symbol_snapshot="count", canonical_unit="count",
                    canonical_factor=Decimal(1), quantity_canonical=item["quantity_original"])
        recipe = next(r for r in bad["tables"]["recipes"] if r["id"] == item["recipe_id"])
        recipe["recipe_hash"] = recipe_hash(recipe_contract(
            recipe, [i for i in bad["tables"]["recipe_items"] if i["recipe_id"] == recipe["id"]],
            {u["id"]: u for u in bad["tables"]["units"]}, {i["id"]: i for i in bad["tables"]["ingredients"]}))
        self.assert_invalid(bad, "INGREDIENT_DIMENSION")

    def test_malformed_json_and_sidecar_report_reason_codes(self):
        bad = deepcopy(self.bundle)
        bad["tables"]["products"][0]["provenance"] = []
        self.assert_invalid(bad, "SCALAR_TYPE_PRECISION")
        bad = deepcopy(self.bundle)
        bad["evaluation"]["choices"][0]["utility"] = float("nan")
        self.assert_invalid(bad, "EVALUATION_SHAPE", reseal=False)

    def test_artifacts_separate_evaluation_and_are_exclusive(self):
        with TemporaryDirectory(prefix="hvtra-synthetic-unit-") as parent:
            destination = Path(parent) / "dataset"
            hashes = write_artifacts(self.bundle, destination)
            self.assertNotIn('"profiles"', (destination / "dataset.json").read_text())
            self.assertIn('"profiles"', (destination / "evaluation.json").read_text())
            self.assertEqual(4, len(hashes))
            with self.assertRaises(FileExistsError):
                write_artifacts(self.bundle, destination)
        with self.assertRaises(ValueError):
            write_artifacts(self.bundle, Path(__file__).resolve().parents[3] / "denied-synthetic-artifacts")

    def test_wrong_target_rejected_before_network(self):
        args = dict(port=57000, database="ingredient_poc_fresh_aabbccddeeff",
                    server_uuid="c7126480-0873-4fc2-83cf-dc7e56fe04b8",
                    shared_server_uuid="a5116344-6161-11f1-b832-aac623966e32",
                    username="poc_writer_aabbccddeeff", password="unit-only")
        for override in ({"database": "recommend_app"}, {"port": 3306},
                         {"server_uuid": args["shared_server_uuid"]}, {"username": "root"}):
            with self.subTest(override=override), self.assertRaises(ValueError):
                # Port 3306 is reserved for shared/default endpoints in this runner contract.
                ResearchTarget(**{**args, **override})
        with self.assertRaises(TypeError):
            SyntheticStore(object())
