from dataclasses import replace
from collections import Counter
from datetime import timedelta
from decimal import Decimal
import json
from types import SimpleNamespace
from unittest import TestCase
from unittest.mock import Mock, patch

import httpx

from app.recommendation.taste_ranking_artifact import RerankingResult
from app.recommendation.taste_ranking_features import level2_hybrid_scores, ranking_features, taste_groups
from app.recommendation.v1_model import CatalogProduct, PurchaseCount, build_artifact, fingerprint
from app.repositories.explore_history_repository import ExploreHistoryError, build_history
from app.service.explore_recommendation_service import ExploreRecommendationService, FamiliarRecommendationService
from app.service.level2_availability import AvailabilityUnavailable, Level2Availability
from app.schemas.taste_recommendation import TastePreviewRequest
from app.service.personalized_taste_features import (
    PersonalizedTasteFeatures, ProductTasteFeatures, TasteFeatureBatch, VariantTasteFeatures,
)
from app.service.taste_recommendation_service import preview_taste
from app.training.level2_evidence_export import readiness, verify_source_packets
from app.training.taste_ranking_dataset import DatasetBlocked, EvidenceBundle, _projection
from tests.test_taste_ranking_pipeline import BASE, build, evidence_json, stamp, uid


def receipt(number, day, lines):
    time = (BASE + timedelta(days=day)).replace(tzinfo=None)
    digest = fingerprint({"customer_id": uid(50), "occurred_at": time.isoformat(), "lines": lines})
    return uid(number), time, digest, lines


def line(number, product, variant, quantity):
    return {"order_item_id": uid(number), "product_id": uid(product), "product_variant_id": uid(variant),
            "quantity": quantity, "purchase_type": "PRODUCT"}


def evidence_v2():
    raw = evidence_json()
    raw["schema_version"] = 2
    for window in raw["windows"]:
        window.update(availability_as_of=window["cutoff"], availability_observed_at=window["cutoff"],
                      availability_source="fixture")
        window["catalog_variants"] = {row["id"]: row["productId"] for row in window["taste_catalog"]["payload"]["variants"]}
        window["availability"] = [
            {"variant_id": variant, "product_id": product, "catalog_sellable": True, "available_quantity": "3"}
            for variant, product in window["catalog_variants"].items()
        ]
        for customer in window["customers"]:
            if customer["survey"]:
                customer["linked_at"] = customer["survey"]["submittedAt"]
    return raw


class Level2HistoryTests(TestCase):
    def test_order_line_quantity_and_return_preference_have_distinct_units(self):
        first = receipt(101, -8, [line(11, 1, 21, "4"), line(12, 1, 22, "2")])
        second = receipt(102, -4, [line(13, 1, 21, "3")])
        fully_returned = receipt(103, -3, [line(14, 2, 23, "1")])
        partial = (uid(31), uid(101), uid(11), uid(21), "1.5", "CHANGE_OF_MIND")
        operational = (uid(32), uid(103), uid(14), uid(23), "1", "WRONG_ITEM")
        history = build_history(uid(50), [first, second, first, fully_returned], (), (partial, operational), (partial,))
        self.assertEqual(history.valid_order_count, 2)
        self.assertEqual(history.purchased_ids, {uid(1), uid(2)})
        self.assertEqual(history.product_counts, {uid(1): 3})
        features = ranking_features(CatalogProduct(uid(1), "A", "Tea", None), history, BASE, 0.0, 0.5, 0,
                                    TasteFeatureBatch("SURVEY_MISSING"))
        self.assertEqual(features.retained_order_frequency, 2)
        self.assertEqual(features.frequency, 3)
        self.assertEqual(features.recency_days, 4)
        self.assertEqual(features.repeat_interval_days, 4)
        self.assertEqual(features.cycle_deviation_days, 0)
        self.assertEqual(features.net_quantity, 7.5)
        self.assertEqual(features.returned_line_rate, 1 / 3)
        self.assertEqual(features.return_reasons, {"CHANGE_OF_MIND": 1})
        self.assertEqual(history.product_history[uid(2)].return_reasons, {"WRONG_ITEM": 1})

    def test_one_purchase_does_not_supply_an_interval_and_conflicting_order_fails(self):
        order = receipt(101, -1, [line(11, 1, 21, "1")])
        history = build_history(uid(50), [order], (), ())
        features = ranking_features(CatalogProduct(uid(1), "A", "Tea", None), history, BASE, 0, 1, 0,
                                    TasteFeatureBatch("SURVEY_MISSING"))
        self.assertIsNone(features.repeat_interval_days)
        with self.assertRaises(ExploreHistoryError):
            build_history(uid(50), [order, receipt(101, -2, [line(11, 1, 21, "2")])], (), ())

    def test_same_preparation_averages_and_incompatible_preparations_remain_missing(self):
        variant = VariantTasteFeatures(uid(21), uid(31), 1, 1, "composition", "HOT", "hash",
                                       {"FLAVOR": True}, {"FLAVOR": 0.8})
        second = replace(variant, variant_id=uid(22), hard_matches={"FLAVOR": False})
        product = CatalogProduct(uid(1), "A", "Tea", None)
        history = build_history(uid(50), [], (), ())
        def features(other, available):
            batch = TasteFeatureBatch("TASTE_FEATURE_READY", products={
                uid(1): ProductTasteFeatures("AVAILABLE", (variant, other)),
            })
            return ranking_features(product, history, BASE, 0, 1, 0, batch, available)
        self.assertEqual(taste_groups(features(second, {uid(21), uid(22)})), ({"FLAVOR": 0.5}, {"FLAVOR": 0.8}))
        different = replace(second, preparation_code="COLD")
        self.assertFalse(features(different, {uid(21), uid(22)}).has_taste)
        self.assertTrue(features(different, {uid(21)}).has_taste)


class Level2DatasetTests(TestCase):
    def test_explore_label_requires_the_first_purchase_to_be_retained(self):
        raw = evidence_v2()
        first = raw["receipts"][6]
        second = dict(first, order_id=uid(3000), occurred_at=stamp(BASE + timedelta(days=2)),
                      observed_at=stamp(BASE + timedelta(days=2)))
        second["evidence_hash"] = fingerprint({
            "customer_id": first["customer_id"],
            "occurred_at": (BASE + timedelta(days=2)).replace(tzinfo=None).isoformat(),
            "lines": first["lines"],
        })
        raw["receipts"].append(second)
        raw["returns"].append({
            "source": "SIGNED_PRODUCT_RETURN", "return_item_id": uid(3001), "customer_id": first["customer_id"],
            "order_id": first["order_id"], "order_item_id": "line", "variant_id": uid(13), "quantity": "1",
            "reason": "CHANGE_OF_MIND", "occurred_at": stamp(BASE + timedelta(days=1, hours=1)),
            "observed_at": stamp(BASE + timedelta(days=1, hours=1)),
        })
        query = build(raw).queries[0]
        self.assertFalse(next(sample.label for sample in query.samples if sample.features.product_id == uid(3)))

    def test_as_of_stock_filters_both_modes_and_fixture_remains_blocked(self):
        raw = evidence_v2()
        stock = raw["windows"][0]["availability"]
        for row in stock:
            if row["product_id"] in (uid(1), uid(3)):
                row["available_quantity"] = "0"
        for row in raw["windows"][0]["catalog"]:
            if row["product_id"] in (uid(1), uid(3)):
                row["commercial"] = False
        bundle = EvidenceBundle.model_validate_json(json.dumps(raw))
        dataset = build(raw)
        first_explore, first_familiar = dataset.queries[:2]
        self.assertEqual({row.features.product_id for row in first_explore.samples}, {uid(4)})
        self.assertEqual({row.features.product_id for row in first_familiar.samples}, {uid(2)})
        self.assertEqual(first_familiar.samples[0].features.sellable_variants, 1)
        self.assertEqual(readiness(dataset, bundle)["status"], "DATASET_BLOCKED")

    def test_late_link_missing_stock_and_synthetic_provenance_are_rejected(self):
        raw = evidence_v2()
        raw["windows"][0]["customers"][0]["linked_at"] = stamp(BASE + timedelta(seconds=1))
        with self.assertRaisesRegex(DatasetBlocked, "SURVEY_LINK_NOT_POINT_IN_TIME"):
            build(raw)
        raw = evidence_v2()
        raw["windows"][0]["availability"].pop()
        with self.assertRaisesRegex(DatasetBlocked, "INCOMPLETE_AVAILABILITY_VARIANT_COVERAGE"):
            build(raw)
        raw = evidence_v2()
        raw["source_kind"] = "VERIFIED_SOURCE_EXPORT"
        raw["history_start"] = stamp(BASE - timedelta(days=20))
        for name in ("customer", "order", "returns", "interactions", "catalog", "taste_catalog", "inventory"):
            raw.setdefault("coverage", {})[name] = {
                "provenance": "VERIFIED_COMMERCIAL", "source_reference": name + ".json", "source_sha256": "b" * 64,
                "observed_from": raw["history_start"], "complete_through": raw["exported_at"], "unresolved_gaps": 0,
            }
        raw["interaction_purchases"][0]["provenance"] = "SYNTHETIC"
        with self.assertRaisesRegex(DatasetBlocked, "NON_COMMERCIAL_OR_OUTSIDE_SOURCE_COVERAGE"):
            build(raw)
        with self.assertRaisesRegex(DatasetBlocked, "INCOMPLETE_SOURCE_LEDGER"):
            verify_source_packets(EvidenceBundle.model_validate_json(json.dumps(evidence_v2())), None)

    def test_online_candidates_and_full_feature_rows_match_cutoff_dataset(self):
        raw = evidence_v2()
        bundle = EvidenceBundle.model_validate_json(json.dumps(raw))
        dataset = build(raw)
        window = bundle.windows[0]
        customer = window.customers[0]
        products = tuple(CatalogProduct(row.product_id, row.product_code, row.name, row.category_code)
                         for row in window.catalog)
        history = build_history(customer.customer_id, (row.history_row() for row in bundle.receipts
                                if row.customer_id == customer.customer_id and row.observed_at <= BASE), (), ())
        counts = Counter((row.customer_id, row.product_id) for row in bundle.interaction_purchases
                         if row.observed_at <= BASE)
        catalog, histories, stock, store, client = (Mock() for _ in range(5))
        catalog.active_catalog.return_value = products
        histories.history.return_value = history
        histories.commercial_variants.return_value = window.catalog_variants
        stock.available_variants.side_effect = lambda variants: variants
        store.load.return_value = build_artifact(products, tuple(PurchaseCount(u, p, n) for (u, p), n in counts.items()))
        client.latest.return_value = customer.survey
        taste_features = PersonalizedTasteFeatures(client, lambda: _projection(window))
        for service_class, query in zip((ExploreRecommendationService, FamiliarRecommendationService), dataset.queries):
            ranker = Mock(artifact=SimpleNamespace(mode=query.mode))
            ranker.score.return_value = RerankingResult("fallback")
            service = service_class(store, histories, catalog, taste_features=taste_features,
                                    taste_reranker=ranker, availability=stock)
            with patch("app.service.explore_recommendation_service.datetime") as clock:
                clock.now.return_value = BASE
                service.recommend(None, customer.customer_id, 5)
            actual = {row.product_id: row for row in ranker.score.call_args.args[0]}
            self.assertEqual(actual, {sample.features.product_id: sample.features for sample in query.samples})

    def test_level1_preview_uses_verified_projection_and_exact_rules(self):
        bundle = EvidenceBundle.model_validate_json(json.dumps(evidence_v2()))
        window = bundle.windows[0]
        view = _projection(window)
        request = TastePreviewRequest.model_validate_json(json.dumps({
            "taxonomyVersion": bundle.taxonomy_version, "taxonomySha256": bundle.taxonomy_sha256,
            "preparationCode": view.payload.preparationCodes[0], "limit": 5,
            "hardGroups": [{"attribute": "BITTERNESS", "allowedValues": ["LOW"]}], "requestSequence": 1,
        }))
        result = preview_taste(request, lambda: view)
        self.assertGreater(result.matchedProductCount, 0)
        self.assertEqual(result.rankingSource, "TASTE_RULES_V1")
        self.assertTrue(result.items)
        stale = preview_taste(request, lambda: replace(view, status="STALE"))
        self.assertFalse(stale.items)
        self.assertEqual(stale.status, "TASTE_CATALOG_UNAVAILABLE")


class Level2ServingTests(TestCase):
    def test_threshold_candidate_modes_and_flat_signal_match_offline_policy(self):
        products = (CatalogProduct(uid(1), "A", "Black tea", None), CatalogProduct(uid(2), "B", "White tea", None))
        store, catalog, history_repo, availability = (Mock() for _ in range(4))
        store.load.return_value = build_artifact(products, (PurchaseCount(uid(50), uid(1), 1),))
        catalog.active_catalog.return_value = products
        history_repo.commercial_variants.return_value = {uid(21): uid(1), uid(22): uid(2), uid(23): uid(2)}
        availability.available_variants.side_effect = lambda mirror: mirror
        explore = ExploreRecommendationService(store, history_repo, catalog, availability=availability)
        familiar = FamiliarRecommendationService(store, history_repo, catalog, availability=availability)
        for count in (4, 5):
            history_repo.history.return_value = build_history(
                uid(50), [receipt(100+i, -i, [line(11, 1, 21, "1")]) for i in range(count)], (), ())
            for service, expected in ((explore, uid(2)), (familiar, uid(1))):
                result = service.recommend(None, uid(50), 5)
                if count == 4:
                    self.assertEqual(result.status, "INSUFFICIENT_VALID_ORDERS")
                else:
                    self.assertEqual([item.product_id for item in result.product.items], [expected])
                    self.assertEqual(result.product.items[0].score, 0)
        self.assertEqual(level2_hybrid_scores({uid(1): 0}, {uid(1): 0}, (uid(1),)), ({uid(1): 0}, False))
        availability.available_variants.side_effect = AvailabilityUnavailable("offline")
        self.assertEqual(explore.recommend(None, uid(50), 5).product.status, "AVAILABILITY_UNAVAILABLE")

    def test_inventory_single_store_and_response_identity(self):
        settings = SimpleNamespace(level2_inventory_service_url="http://127.0.0.1:1",
                                   level2_stock_timeout_seconds=2, catalog_service_url="http://127.0.0.1:2")
        def variant(number):
            return SimpleNamespace(id=uid(number), productId=uid(1), sellable=True, status="ACTIVE",
                                   sku="SKU", sellingPrice=Decimal("10"))
        with (patch("app.service.level2_availability.CatalogClient") as catalog,
              patch("app.service.level2_availability.httpx.Client") as http,
              patch("app.service.level2_availability.generate_internal_auth_headers", return_value={})):
            catalog.return_value.__enter__.return_value.get_product_variants.return_value = [variant(21), variant(22)]
            client = http.return_value.__enter__.return_value
            client.post.return_value = httpx.Response(200, request=httpx.Request("POST", "http://fixture"), json=[
                {"variantId": uid(21), "availableQuantity": "2"}, {"variantId": uid(22), "availableQuantity": "0"},
            ])
            self.assertEqual(Level2Availability(settings).available_variants({uid(21): uid(1), uid(22): uid(1)}),
                             {uid(21): uid(1)})
            self.assertEqual(client.post.call_args.kwargs["json"], {"variantIds": [uid(21), uid(22)]})
            with self.assertRaises(AvailabilityUnavailable):
                Level2Availability(settings).available_variants({uid(21): uid(2)})
