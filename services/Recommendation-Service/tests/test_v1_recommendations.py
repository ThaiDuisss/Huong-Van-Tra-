from contextlib import contextmanager, redirect_stdout
from dataclasses import replace
import importlib
import io
import json
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient
import numpy as np
from sqlalchemy import create_engine, event, text
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session

from app.recommendation.v1_artifact import V1ArtifactStore
from app.recommendation.v1_data import readonly_session
from app.recommendation.v1_model import (
    CatalogProduct, CONTRACT_VERSION, PurchaseCount, V1ModelError,
    build_artifact, feature_tokens, hybrid_scores, normalize_scores,
)
from app.repositories.v1_recommendation_repository import CustomerPurchaseHistory, V1RecommendationRepository
from app.service.recommendation_service import RecommendationService


def catalog():
    return (
        CatalogProduct("a", "A", "Lotus tea", "tea", ("lotus", "leaf")),
        CatalogProduct("b", "B", "Lotus floral", "tea", ("lotus", "leaf")),
        CatalogProduct("c", "C", "Ginger herbal", "herbal", ("ginger",)),
        CatalogProduct("d", "D", "Black tea", "tea", ("black",)),
        CatalogProduct("e", "E", "Fruit berry", "fruit", ("berry",)),
        CatalogProduct("f", "F", "Fruit citrus", "fruit", ("citrus",)),
    )


def artifact():
    return build_artifact(catalog(), (
        PurchaseCount("u", "a", 3), PurchaseCount("u", "c", 1),
        PurchaseCount("v", "a", 2), PurchaseCount("v", "c", 2),
        PurchaseCount("w", "d", 2), PurchaseCount("w", "e", 1),
    ))


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.repository = MagicMock(spec=V1RecommendationRepository)
        self.repository.active_catalog.return_value = catalog()
        self.repository.popularity.return_value = [("b", "B", "Lotus floral", 20)]
        self.store = MagicMock(spec=V1ArtifactStore)
        self.store.load.return_value = artifact()
        self.service = RecommendationService(self.store, self.repository)

    def assert_route(self, orders, source):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(orders, {"a": 3})
        result = self.service.recommend(None, "u", 5)
        self.assertEqual(result.source, source)
        self.assertEqual(result.purchase_order_count, orders)
        if source == "POPULARITY":
            self.store.load.assert_not_called()
            self.repository.active_catalog.assert_not_called()
        else:
            self.store.load.assert_called_once()
            self.assertEqual(result.model_type, "personalized_hybrid")
        return result

    def test_zero_orders_popularity(self): self.assert_route(0, "POPULARITY")
    def test_one_order_popularity(self): self.assert_route(1, "POPULARITY")
    def test_four_orders_popularity(self): self.assert_route(4, "POPULARITY")
    def test_five_orders_hybrid(self): self.assert_route(5, "HYBRID")
    def test_six_orders_hybrid(self): self.assert_route(6, "HYBRID")

    def test_exclude_true_filters_before_top_k_and_false_allows_repurchase(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {"a": 3})
        included = self.service.recommend(None, "u", 6, False)
        excluded = self.service.recommend(None, "u", 5, True)
        self.assertIn("a", [p.product_id for p in included.items])
        self.assertNotIn("a", [p.product_id for p in excluded.items])
        self.assertEqual(len(excluded.items), 5)

    def test_popularity_both_exclusion_modes(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(1, {"a": 3})
        self.service.recommend(None, "u", 5)
        self.repository.popularity.assert_called_with(None, {"a"}, 5)
        self.service.recommend(None, "u", 5, False)
        self.repository.popularity.assert_called_with(None, set(), 5)

    def test_missing_artifact_fallback(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {"a": 3})
        self.store.load.side_effect = V1ModelError("missing")
        result = self.service.recommend(None, "u", 5, False)
        self.assertEqual((result.source, result.fallback_reason), ("FALLBACK", "V1_MODEL_NOT_AVAILABLE"))
        self.assertEqual(result.model_type, "popularity")

    def test_both_flat_fallback(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {"outside-catalog": 3})
        result = self.service.recommend(None, "u", 5)
        self.assertEqual(result.fallback_reason, "BOTH_COMPONENTS_FLAT")

    def test_no_candidates_returns_empty(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {p.product_id: 1 for p in catalog()})
        result = self.service.recommend(None, "u", 5)
        self.assertEqual((result.source, result.items), ("HYBRID", []))

    def test_catalog_change_requires_refresh(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {"a": 3})
        self.repository.active_catalog.return_value = catalog() + (CatalogProduct("g", "G", "New tea", "tea", ("leaf",)),)
        result = self.service.recommend(None, "u", 5)
        self.assertEqual(result.fallback_reason, "V1_MODEL_NOT_AVAILABLE")

    def test_bom_change_requires_refresh(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {"a": 3})
        self.repository.active_catalog.return_value = (replace(catalog()[0], ingredient_codes=("other",)),) + catalog()[1:]
        self.assertEqual(self.service.recommend(None, "u", 5).source, "FALLBACK")

    def test_database_errors_are_not_swallowed(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {"a": 3})
        self.repository.active_catalog.side_effect = OperationalError("test", {}, Exception("db"))
        with self.assertRaises(OperationalError): self.service.recommend(None, "u", 5)
        self.repository.popularity.assert_not_called()

    def test_recoverable_scoring_error_fallback(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {"a": 3})
        with patch("app.service.recommendation_service.hybrid_scores", side_effect=V1ModelError("bad score")):
            self.assertEqual(self.service.recommend(None, "u", 5).source, "FALLBACK")

    def test_deterministic_product_id_tie_break(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {"a": 3})
        with patch("app.service.recommendation_service.hybrid_scores", return_value=({"f": 0.6, "e": 0.6}, False)):
            self.assertEqual([p.product_id for p in self.service.recommend(None, "u", 2).items], ["e", "f"])


class ModelTests(unittest.TestCase):
    def test_exact_sixty_forty_and_middle_score(self):
        scores, flat = hybrid_scores({"a": 1, "b": 0, "c": .5}, {"a": 0, "b": 1, "c": .5}, ("a", "b", "c"))
        self.assertEqual(scores, {"a": .6, "b": .4, "c": .5})
        self.assertFalse(flat)

    def test_flat_cf_keeps_content_weight(self):
        scores, flat = hybrid_scores({"a": 2, "b": 1}, {"a": 4, "b": 4}, ("a", "b"))
        self.assertEqual(scores, {"a": .6, "b": 0})
        self.assertFalse(flat)

    def test_flat_content_keeps_cf_weight(self):
        scores, flat = hybrid_scores({"a": 4, "b": 4}, {"a": 2, "b": 1}, ("a", "b"))
        self.assertEqual(scores, {"a": .4, "b": 0})
        self.assertFalse(flat)

    def test_both_missing_are_flat(self):
        self.assertEqual(hybrid_scores({}, {}, ("a", "b")), ({"a": 0, "b": 0}, True))

    def test_normalization_only_eligible_candidates(self):
        scores, _ = hybrid_scores({"a": 100, "b": 2, "c": 1}, {}, ("b", "c"))
        self.assertEqual(scores, {"b": .6, "c": 0})

    def test_nonfinite_scores_rejected(self):
        with self.assertRaises(V1ModelError): normalize_scores({"a": float("nan")})

    def test_tfidf_new_product_without_interactions(self):
        model = artifact()
        content, cf = model.components({"a": 1})
        self.assertGreater(content["b"], content["e"])
        self.assertGreater(content["b"], 0)
        self.assertEqual(cf["b"], 0)
        self.assertEqual(model.popularity[1], 0)

    def test_deterministic_vocabulary_and_reversed_input(self):
        first = artifact()
        second = build_artifact(tuple(reversed(catalog())), (
            PurchaseCount("w", "e", 1), PurchaseCount("w", "d", 2),
            PurchaseCount("v", "c", 2), PurchaseCount("v", "a", 2),
            PurchaseCount("u", "c", 1), PurchaseCount("u", "a", 3),
        ))
        self.assertEqual(first.vocabulary, tuple(sorted(first.vocabulary)))
        self.assertEqual(first.vocabulary, second.vocabulary)
        np.testing.assert_array_equal(first.content.toarray(), second.content.toarray())
        np.testing.assert_array_equal(first.similarity.toarray(), second.similarity.toarray())

    def test_smooth_idf_and_l2_vectors(self):
        model = artifact()
        common = model.vocabulary.index("category:tea")
        rare = model.vocabulary.index("ingredient:ginger")
        self.assertGreater(model.idf[rare], model.idf[common])
        np.testing.assert_allclose(np.asarray(model.content.power(2).sum(axis=1)).ravel(), 1)

    def test_name_tokens_unicode_and_bom_dedup(self):
        product = CatalogProduct("a", "A", "Tea Tea", "tea", ("leaf", "leaf"))
        tokens = feature_tokens(product)
        self.assertEqual(tokens["name:tea"], 2)
        self.assertEqual(tokens["ingredient:leaf"], 1)

    def test_cf_cosine_zero_diagonal_and_expected_history_scores(self):
        model = artifact()
        np.testing.assert_array_equal(model.similarity.diagonal(), np.zeros(6))
        self.assertAlmostEqual(model.similarity[0, 2], 7 / np.sqrt(65))
        _, cf = model.components({"a": 2})
        self.assertAlmostEqual(cf["c"], 2 * 7 / np.sqrt(65))
        self.assertEqual(cf["a"], 0)

    def test_negative_history_rejected(self):
        with self.assertRaises(V1ModelError): artifact().components({"a": -1})


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.directory = TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "v1.npz"
        self.store = V1ArtifactStore(self.path)

    def test_atomic_roundtrip_and_cached_identity(self):
        model = artifact()
        self.store.save(model)
        with patch("app.recommendation.v1_artifact.np.load", wraps=np.load) as loader:
            first, second = self.store.load(), self.store.load()
            self.assertIs(first, second)
            self.assertEqual(loader.call_count, 1)
        self.assertEqual(first.components({"a": 2}), model.components({"a": 2}))
        self.assertFalse(first.content.data.flags.writeable)
        self.assertFalse(list(self.path.parent.glob("*.tmp")))

    def test_replacement_invalidates_cache(self):
        self.store.save(artifact())
        first = self.store.load()
        V1ArtifactStore(self.path).save(build_artifact(catalog(), (PurchaseCount("z", "e", 5),)))
        second = self.store.load()
        self.assertIsNot(first, second)
        self.assertNotEqual(first.history_sha256, second.history_sha256)

    def test_missing_file_safe_error(self):
        with self.assertRaises(V1ModelError): self.store.load()

    def test_corrupt_file_clears_cached_model(self):
        self.store.save(artifact())
        self.store.load()
        self.path.write_bytes(b"corrupt")
        with self.assertRaises(V1ModelError): self.store.load()
        self.assertIsNone(self.store._cached)

    def test_bad_contract_rejected(self):
        with self.assertRaises(V1ModelError): self.store.save(replace(artifact(), contract_version="other"))

    def test_incompatible_mapping_rejected(self):
        model = artifact()
        with self.assertRaises(V1ModelError): self.store.save(replace(model, products=model.products[::-1]))

    def test_catalog_fingerprint_rejected(self):
        with self.assertRaises(V1ModelError): self.store.save(replace(artifact(), catalog_sha256="bad"))

    def test_bad_serialization_version_rejected_on_load(self):
        self.store.save(artifact())
        with np.load(self.path, allow_pickle=False) as values:
            data = {key: values[key].copy() for key in values.files}
        meta = json.loads(data["metadata"].item())
        meta["format_version"] = 99
        data["metadata"] = np.asarray(json.dumps(meta))
        np.savez_compressed(self.path, **data)
        with self.assertRaises(V1ModelError): self.store.load()

    def test_tampered_product_mapping_rejected_on_load(self):
        self.store.save(artifact())
        with np.load(self.path, allow_pickle=False) as values:
            data = {key: values[key].copy() for key in values.files}
        meta = json.loads(data["metadata"].item())
        meta["products"][1]["product_id"] = "a"
        data["metadata"] = np.asarray(json.dumps(meta))
        np.savez_compressed(self.path, **data)
        with self.assertRaises(V1ModelError): self.store.load()

    def test_tampered_matrix_shape_rejected_on_load(self):
        self.store.save(artifact())
        with np.load(self.path, allow_pickle=False) as values:
            data = {key: values[key].copy() for key in values.files}
        data["similarity_shape"] = np.array([6, 7])
        np.savez_compressed(self.path, **data)
        with self.assertRaises(V1ModelError): self.store.load()

    def test_invalid_cf_diagonal_rejected(self):
        model = artifact()
        wrong = model.similarity.copy()
        wrong.setdiag(1)
        with self.assertRaises(V1ModelError): self.store.save(replace(model, similarity=wrong))


class RepositoryTests(unittest.TestCase):
    def setUp(self):
        self.engine = create_engine("sqlite:///:memory:")
        self.addCleanup(self.engine.dispose)
        self.db = Session(self.engine)
        self.addCleanup(self.db.close)
        self.repository = V1RecommendationRepository()
        # Isolated SQL fixtures, never the business DB.
        ddl = (
            "CREATE TABLE interaction_events(id TEXT, customer_id TEXT, product_id TEXT, order_id TEXT, event_type TEXT)",
            "CREATE TABLE products(id TEXT, product_code TEXT, name TEXT, category_id TEXT, status TEXT, is_deleted BOOLEAN)",
            "CREATE TABLE categories(id TEXT, category_code TEXT)",
            "CREATE TABLE ingredients(id TEXT, ingredient_code TEXT, type INTEGER)",
            "CREATE TABLE product_ingredients(product_id TEXT, ingredient_id TEXT, is_active_bom BOOLEAN)",
            "CREATE TABLE customer_interactions(customer_id TEXT, product_id TEXT, total_purchase_count INTEGER)",
        )
        for statement in ddl: self.db.execute(text(statement))
        for product in catalog():
            self.db.execute(text("INSERT INTO products VALUES(:id,:code,:name,'cat','ACTIVE',0)"),
                            {"id": product.product_id, "code": product.product_code, "name": product.name})
        self.db.execute(text("INSERT INTO categories VALUES('cat','tea')"))

    def add_events(self, rows):
        for i, (order, product, kind) in enumerate(rows):
            self.db.execute(text("INSERT INTO interaction_events VALUES(:id,'u',:product,:order,:kind)"),
                            {"id": str(i), "product": product, "order": order, "kind": kind})

    def test_three_products_in_one_order_are_one_order_and_popularity(self):
        self.add_events([("one", p, "PURCHASE") for p in ("a", "c", "d")])
        history = self.repository.customer_history(self.db, "u")
        self.assertEqual(history.distinct_order_count, 1)
        store = MagicMock(spec=V1ArtifactStore)
        self.assertEqual(RecommendationService(store).recommend(self.db, "u", 3).source, "POPULARITY")
        store.load.assert_not_called()

    def test_return_and_null_blank_order_ids_never_inflate_routing(self):
        self.add_events([("one", "a", "PURCHASE"), ("two", "a", "RETURN"),
                         (None, "b", "PURCHASE"), (" ", "c", "PURCHASE")])
        history = self.repository.customer_history(self.db, "u")
        self.assertEqual((history.distinct_order_count, history.missing_order_events), (1, 2))
        self.assertEqual(history.product_counts, {"a": 1, "b": 1, "c": 1})
        self.assertEqual(self.repository.missing_purchase_order_events(self.db), 2)

    def test_purchase_then_return_still_excluded(self):
        self.add_events([("one", "a", "PURCHASE"), ("one", "a", "RETURN")])
        result = RecommendationService(MagicMock()).recommend(self.db, "u", 6, True)
        self.assertNotIn("a", [p.product_id for p in result.items])

    def test_exact_ingredient_filter_and_variant_dedup(self):
        for i, kind, active in ((1, 1, 1), (2, 0, 1), (3, None, 1), (4, 1, 0), (5, 2, 1)):
            self.db.execute(text("INSERT INTO ingredients VALUES(:id,:code,:kind)"),
                            {"id": str(i), "code": f"ingredient-{i}", "kind": kind})
            self.db.execute(text("INSERT INTO product_ingredients VALUES('a',:id,:active)"), {"id": str(i), "active": active})
        self.db.execute(text("INSERT INTO product_ingredients VALUES('a','1',1)"))
        products = self.repository.active_catalog(self.db)
        self.assertEqual(products[0].ingredient_codes, ("ingredient-1",))

    def test_active_non_deleted_filter_in_both_paths(self):
        self.db.execute(text("UPDATE products SET status='INACTIVE' WHERE id='e'"))
        self.db.execute(text("UPDATE products SET is_deleted=1 WHERE id='f'"))
        self.assertEqual([p.product_id for p in self.repository.active_catalog(self.db)], list("abcd"))
        self.assertEqual([r[0] for r in self.repository.popularity(self.db, set(), 6)], list("abcd"))

    def test_popularity_event_counts_not_quantity_or_distinct_orders(self):
        self.db.execute(text("INSERT INTO customer_interactions VALUES('x','b',10),('y','a',2)"))
        self.assertEqual([r[0] for r in self.repository.popularity(self.db, set(), 2)], ["b", "a"])

    def test_build_purchase_counts_use_only_purchase(self):
        self.add_events([("one", "a", "PURCHASE"), ("one", "a", "PURCHASE"), ("one", "a", "RETURN")])
        self.assertEqual(self.repository.build_purchase_counts(self.db), (PurchaseCount("u", "a", 2),))

    def test_request_query_budget_and_no_build_per_request(self):
        self.add_events([(str(i), "a", "PURCHASE") for i in range(5)])
        products = self.repository.active_catalog(self.db)
        model = build_artifact(products, (PurchaseCount("u", "a", 5),))
        store = MagicMock(spec=V1ArtifactStore)
        store.load.return_value = model
        statements = []
        event.listen(self.engine, "before_cursor_execute", lambda c, cursor, sql, args, ctx, many: statements.append(sql))
        result = RecommendationService(store).recommend(self.db, "u", 5, False)
        self.assertEqual(result.source, "HYBRID")
        self.assertEqual(len(statements), 4)
        history_queries = [s for s in statements if "interaction_events" in s]
        self.assertEqual(len(history_queries), 2)
        self.assertTrue(all("customer_id =" in s for s in history_queries))


class ApiAndLifecycleTests(unittest.TestCase):
    def setUp(self):
        self.api = importlib.import_module("app.api.recommendations")
        app = FastAPI()
        app.include_router(self.api.router)
        self.repository = MagicMock(spec=V1RecommendationRepository)
        self.repository.customer_history.return_value = CustomerPurchaseHistory(0, {})
        self.repository.popularity.return_value = [("a", "A", "Lotus tea", 5)]
        self.service = RecommendationService(MagicMock(spec=V1ArtifactStore), self.repository)
        app.dependency_overrides[self.api.get_db] = lambda: None
        app.dependency_overrides[self.api.get_recommendation_service] = lambda: self.service
        self.client = TestClient(app)
        self.addCleanup(self.client.close)

    def test_api_default_true_and_response_aliases(self):
        response = self.client.get("/recommendations/u?limit=5")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["excludePurchased"])
        self.assertEqual(data["source"], "POPULARITY")
        self.assertEqual(data["items"][0]["productId"], "a")
        self.assertEqual(data["modelContractVersion"], CONTRACT_VERSION)

    def test_api_false_and_limit_clamp(self):
        response = self.client.get("/recommendations/u?limit=999&exclude_purchased=false")
        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.json()["excludePurchased"])
        self.repository.popularity.assert_called_with(None, set(), 50)

    def test_api_invalid_limit_or_flag(self):
        for query in ("limit=0", "exclude_purchased=invalid"):
            self.assertEqual(self.client.get(f"/recommendations/u?{query}").status_code, 422)

    def test_api_hybrid_response(self):
        self.repository.customer_history.return_value = CustomerPurchaseHistory(5, {"a": 2})
        self.repository.active_catalog.return_value = catalog()
        self.service.artifact_store.load.return_value = artifact()
        data = self.client.get("/recommendations/u?exclude_purchased=false").json()
        self.assertEqual(data["source"], "HYBRID")
        self.assertEqual(data["modelType"], "personalized_hybrid")

    def test_app_import_without_lightfm(self):
        script = "import sys; import app.main; assert not any(n.startswith('lightfm') or n in {'app.training.model_artifact','app.training.dataset_builder','app.training.trainer'} for n in sys.modules)"
        subprocess.run([sys.executable, "-B", "-c", script], check=True, capture_output=True)

    def test_readonly_refresh_session_rolls_back_without_commit(self):
        engine = MagicMock()
        connection = engine.connect.return_value.__enter__.return_value
        with patch("app.core.settings.Settings", return_value=SimpleNamespace(sqlalchemy_database_url="unused")), \
                patch("app.recommendation.v1_data.create_engine", return_value=engine), \
                patch("app.recommendation.v1_data.Session"):
            with readonly_session(): pass
        self.assertEqual([c.args[0] for c in connection.exec_driver_sql.call_args_list], [
            "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ", "SET TRANSACTION READ ONLY",
            "START TRANSACTION WITH CONSISTENT SNAPSHOT"])
        connection.rollback.assert_called_once()
        connection.commit.assert_not_called()

    def test_refresh_command_fixture_writes_separate_artifact(self):
        from app.training import train_v1_hybrid
        with TemporaryDirectory() as directory:
            path = Path(directory) / "v1.npz"
            repository = MagicMock(spec=V1RecommendationRepository)
            repository.active_catalog.return_value = catalog()
            repository.build_purchase_counts.return_value = (PurchaseCount("u", "a", 5),)
            repository.missing_purchase_order_events.return_value = 2

            @contextmanager
            def fixture_session(*args):
                yield None, SimpleNamespace(v1_artifact_path=str(path), model_artifact_path=str(Path(directory)/"legacy.pkl"))

            with patch.object(train_v1_hybrid, "readonly_session", fixture_session), \
                    patch.object(train_v1_hybrid, "V1RecommendationRepository", return_value=repository), \
                    patch.object(sys, "argv", ["train_v1_hybrid"]), redirect_stdout(io.StringIO()):
                train_v1_hybrid.main()
            self.assertEqual(V1ArtifactStore(path).load().missing_purchase_order_events, 2)
            self.assertFalse((Path(directory)/"legacy.pkl").exists())


if __name__ == "__main__":
    unittest.main()
