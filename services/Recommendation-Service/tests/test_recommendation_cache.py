from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
from threading import Barrier, Lock
from types import SimpleNamespace
from unittest import TestCase
from unittest.mock import patch
from uuid import UUID

from app.core.settings import Settings
from app.repositories.explore_history_repository import ExploreHistoryError
from app.repositories.ingredient_history_repository import RetainedLine
from app.service.ingredient_recommendation_service import direct_grams
from app.service.level2_serving_inputs import Level2ServingInputs
from app.service.personalized_taste_features import ProductTasteFeatures, TasteFeatureBatch
from app.service.recommendation_cache import RecommendationCache


class RecommendationCacheTests(TestCase):
    def test_concurrent_readers_share_one_load_and_customer_keys_are_isolated(self):
        cache = RecommendationCache(5, 2)
        start = Barrier(4)
        lock = Lock()
        load_count = 0
        payload = object()

        def load():
            nonlocal load_count
            with lock:
                load_count += 1
            return payload

        def request(_):
            start.wait()
            return cache.get("customer-a", load)

        with ThreadPoolExecutor(max_workers=4) as workers:
            self.assertEqual(list(workers.map(request, range(4))), [payload] * 4)
        self.assertEqual(load_count, 1)
        self.assertEqual(cache.get("customer-b", lambda: "different"), "different")

    def test_ttl_starts_with_load_and_capacity_evicts_least_recent(self):
        clock = [100]
        cache = RecommendationCache(5, 2)

        def load():
            clock[0] += 2
            return "v1"

        with patch("app.service.recommendation_cache.monotonic", side_effect=lambda: clock[0]):
            self.assertEqual(cache.get("a", load), "v1")
            self.assertEqual(cache.get("b", lambda: "b"), "b")
            clock[0] = 105
            self.assertEqual(cache.get("a", lambda: "v2"), "v2")
            self.assertEqual(cache.get("c", lambda: "c"), "c")
            self.assertEqual(cache.get("b", lambda: "reloaded"), "reloaded")

    def test_error_is_never_cached(self):
        cache = RecommendationCache(5, 1)

        def fail():
            raise RuntimeError("temporary outage")

        with self.assertRaises(RuntimeError):
            cache.get("a", fail)
        self.assertEqual(cache.get("a", lambda: "recovered"), "recovered")

    def test_stock_scope_preserves_candidate_specific_taste_status(self):
        batch = TasteFeatureBatch("TASTE_FEATURE_READY", products={
            "out-of-stock": ProductTasteFeatures("AVAILABLE"),
            "sensory-missing": ProductTasteFeatures("SENSORY_MISSING"),
        }, sellable_variant_counts={"out-of-stock": 2, "sensory-missing": 1})
        scoped = batch.for_products({"sensory-missing"})
        self.assertEqual(scoped.status, "SENSORY_MISSING")
        self.assertEqual(scoped.sellable_variant_counts, {"sensory-missing": 1})
        self.assertEqual(set(scoped.products), {"sensory-missing"})

    def test_level2_serving_inputs_caches_units_stock_and_variant_availability(self):
        cfg = Settings(
            _env_file=None,
            mysql_user="fixture",
            mysql_password="fixture",
            app_environment="test",
            internal_header_secret="isolated-recommendation-context-secret-32",
            internal_replay_backend="memory",
            level2_catalog_cache_seconds=30,
            level2_stock_cache_seconds=8,
        )
        inputs = Level2ServingInputs(cfg)
        try:
            ing_a, ing_b = UUID(int=1), UUID(int=2)
            with (
                patch("app.service.level2_serving_inputs.warehouse_ingredient_units", return_value={ing_a: [UUID(int=9)]}) as units_fn,
                patch("app.service.level2_serving_inputs.warehouse_ingredient_stock", return_value={ing_a: Decimal(5), ing_b: Decimal(3)}) as stock_fn,
                patch("app.service.level2_serving_inputs.fetch_available_variant_ids", return_value={"v1", "v2"}) as avail_fn,
            ):
                self.assertEqual(inputs.warehouse_units(), {ing_a: [UUID(int=9)]})
                self.assertEqual(inputs.warehouse_units(), {ing_a: [UUID(int=9)]})
                self.assertEqual(units_fn.call_count, 1)

                self.assertEqual(inputs.warehouse_stock({}, None, units={}), {ing_a: Decimal(5), ing_b: Decimal(3)})
                self.assertEqual(inputs.warehouse_stock({}, None, units={}), {ing_a: Decimal(5), ing_b: Decimal(3)})
                self.assertEqual(stock_fn.call_count, 1)

                self.assertEqual(inputs.available_variant_ids({"v1", "v2", "v3"}, cfg), frozenset({"v1", "v2"}))
                self.assertEqual(inputs.available_variant_ids({"v1", "v2", "v3"}, cfg), frozenset({"v1", "v2"}))
                self.assertEqual(avail_fn.call_count, 1)
        finally:
            inputs.close()

    def test_malformed_bundle_component_raises_explore_history_error(self):
        bad_line = RetainedLine(
            UUID(int=10),
            None,
            UUID(int=11),
            Decimal(1),
            components=({"ingredient_id": "not-a-uuid", "quantity": "10", "unit": "g"},),
            purchase_type="CUSTOM_BUNDLE",
        )
        with self.assertRaises(ExploreHistoryError):
            direct_grams(bad_line)
