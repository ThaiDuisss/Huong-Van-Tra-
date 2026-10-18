from dataclasses import replace
import unittest

from app.recommendation.v1_model import CatalogProduct
from offline_benchmark.runners.verify_v1_60_40 import evaluate_tfidf, replace_test_products
from offline_benchmark.split.chronological_split import chronological_split
from offline_benchmark.tests.test_benchmark import fixture


class VerificationTests(unittest.TestCase):
    def inputs(self):
        snapshot = fixture()
        products = tuple(CatalogProduct(p.product_id, p.product_id.upper(),
                                         "Tea " + p.product_id, p.category_code, p.ingredient_codes)
                         for p in snapshot.products)
        return snapshot, chronological_split(snapshot.purchases), products

    def test_exact_runtime_tfidf_on_fixture_and_same_cohort(self):
        snapshot, splits, products = self.inputs()
        full, routed = evaluate_tfidf(snapshot, splits, products)
        self.assertEqual(full["aggregate"]["evaluated_customers"], 2)
        self.assertEqual(routed["sources"], {"HYBRID": 2})
        self.assertEqual(full["training_events"], 16)

    def test_mutating_all_test_labels_preserves_both_variant_rankings(self):
        snapshot, splits, products = self.inputs()
        before = evaluate_tfidf(snapshot, splits, products)
        after = evaluate_tfidf(snapshot, tuple(replace_test_products(s) for s in splits), products)
        for first, second in zip(before, after):
            self.assertEqual([r["top5"] for r in first["customers"]], [r["top5"] for r in second["customers"]])

    def test_routing_variant_uses_train_order_count_not_eligibility(self):
        snapshot, splits, products = self.inputs()
        snapshot = replace(snapshot, purchases=tuple(e for e in snapshot.purchases if int(e.event_id.split('-')[1]) < 5))
        full, routed = evaluate_tfidf(snapshot, chronological_split(snapshot.purchases), products)
        # Each eligible customer has four TRAIN orders; the approved online
        # threshold is two, independently of benchmark eligibility (five total).
        self.assertEqual(routed["sources"], {"HYBRID": 2})
        self.assertEqual(routed["aggregate"], full["aggregate"])
        self.assertEqual([(r["customer_id"], r["top5"]) for r in routed["customers"]],
                         [(r["customer_id"], r["top5"]) for r in full["customers"]])
        self.assertEqual(full["aggregate"]["evaluated_customers"], 2)

    def test_runtime_catalog_mismatch_stops_verification(self):
        snapshot, splits, products = self.inputs()
        with self.assertRaises(ValueError): evaluate_tfidf(snapshot, splits, products[:-1])


if __name__ == "__main__":
    unittest.main()
