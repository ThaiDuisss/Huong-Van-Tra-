from datetime import datetime, timedelta
from decimal import Decimal
import hashlib
import hmac
from io import BytesIO
import json
from types import SimpleNamespace
from unittest import TestCase
from unittest.mock import Mock, patch
from uuid import UUID

from openpyxl import load_workbook

from app.kafka.receipt_projection import project_receipt, replay_projection
from app.kafka.signed_order_event import OrderIngressError, POLICY, _node, _text, verify_order_event
from app.models.business_report import CompletedPurchaseOrder
from app.repositories.ingredient_history_repository import RetainedLine
from app.service.ingredient_recommendation_service import (
    DIRECT, PROXY, behavioral_profile, catalog_signals, direct_grams,
)
from app.service.ingredient_report_workbook import build_ingredient_workbook
from app.service.order_inbox_service import OrderInboxService, _receipt_line


class IngredientRecommendationTests(TestCase):
    def test_quantity_cap_recency_source_weight_and_normalized_share(self):
        now = datetime(2026, 11, 27)
        product, variant, first, second = (UUID(int=i) for i in range(1, 5))
        recipe = SimpleNamespace(row=SimpleNamespace(productId=product), grams={first: Decimal(100)})
        bundle_components = (
            {"ingredient_id": str(first), "quantity": "500", "unit": "g"},
            {"ingredient_id": str(second), "quantity": "0.5", "unit": "kg"},
        )
        lines = [
            RetainedLine(UUID(int=5), product, variant, Decimal(8), "order-1", now - timedelta(days=60)),
            RetainedLine(UUID(int=6), None, variant, Decimal(1), "order-2", now,
                         components=bundle_components, purchase_type="CUSTOM_BUNDLE"),
            RetainedLine(UUID(int=7), None, variant, Decimal(0), "order-3", now,
                         components=bundle_components, purchase_type="CUSTOM_BUNDLE"),
        ]
        config = SimpleNamespace(ingredient_proxy_weight=1.0, ingredient_direct_weight=1.5,
                                 ingredient_retained_quantity_cap=5, ingredient_recency_half_life_days=60)
        scores, sources = behavioral_profile(lines, {variant: recipe}, now, config)
        self.assertEqual(direct_grams(lines[1]), {first: Decimal(500), second: Decimal(500)})
        self.assertEqual(scores[first], 1.0)
        self.assertAlmostEqual(scores[second], 0.36720043, places=7)
        self.assertEqual(sources[first], {DIRECT, PROXY})
        self.assertEqual(sources[second], {DIRECT})

    def test_pair_zero_for_sparse_signals_and_selected_ingredients(self):
        selected, often, sparse = (UUID(int=i) for i in range(1, 4))
        recipes = [SimpleNamespace(grams=dict.fromkeys(members, 1))
                   for members in ((selected, often, sparse), (selected, often), (often,))]
        prior, pair = catalog_signals(recipes, {selected}, min_pair_boms=2)
        self.assertEqual(prior, {selected: 2 / 3, often: 1, sparse: 1 / 3})
        self.assertEqual(dict(pair), {often: 2 / 6 ** 0.5})

    def test_signed_components_replay_and_legacy_purchase_shape(self):
        order_id, event_id, variant_id, line_id, ingredient = (str(UUID(int=i)) for i in range(1, 6))
        topic = "order.completed"
        key = b"local-test-ingredient-receipt-key"
        item = dict(orderItemId=line_id, productVariantId=variant_id, quantity=1, purchaseType="CUSTOM_BUNDLE",
                    components=[dict(ingredientId=ingredient, ingredientCode="ING-1", quantity=100, unit="g")])
        body = dict(eventId=event_id, eventType="ORDER_COMPLETED", eventVersion=1, sourceService="order-service",
                    orderId=order_id, eventKey=order_id, customerId=None, items=[item])
        context = b"".join(_text(b"s", v) for v in (POLICY, topic, order_id, "test", "1"))
        auth = dict(keyId="test", keyVersion="1", signature=hmac.digest(key, context + _node(body), hashlib.sha256).hex())
        headers = dict(event_id=event_id, event_type="ORDER_COMPLETED", event_version="1",
                       source_service="order-service", aggregate_type="ORDER", aggregate_id=order_id,
                       business_key=order_id + ":order-completed")
        msg = SimpleNamespace(
            value=lambda: json.dumps(dict(body, kafkaAuth=auth)).encode(), key=lambda: order_id.encode(),
            headers=lambda: headers.items(), topic=lambda: topic, offset=lambda: 1, partition=lambda: 0,
        )
        signed = verify_order_event(msg, event_type="ORDER_COMPLETED", topic=topic, keys={("test", "1"): key})
        approved = project_receipt(signed.raw, signed.headers, keys={"1": key}, key_version="1")
        stored = _receipt_line(replay_projection(approved)["items"][0])
        self.assertEqual(stored["components"], [
            dict(ingredient_id=ingredient, ingredient_code="ING-1", quantity="100", unit="g"),
        ])
        item["components"][0]["quantity"] = 101
        with self.assertRaisesRegex(OrderIngressError, "INVALID_KAFKA_SIGNATURE"):
            verify_order_event(msg, event_type="ORDER_COMPLETED", topic=topic, keys={("test", "1"): key})
        legacy = dict(orderItemId=line_id, productVariantId=variant_id, quantity=1)
        self.assertEqual(_receipt_line(legacy)["purchase_type"], "PRODUCT")
        self.assertNotIn("components", _receipt_line(dict(legacy, purchaseType="CUSTOM_BUNDLE")))

    def test_custom_bundle_projects_whole_order_without_product_interaction(self):
        order_id, event_id, customer_id, variant_id, line_id, ingredient = (str(UUID(int=i)) for i in range(1, 7))
        row = SimpleNamespace(order_id=order_id, event_id=event_id, observed_at=datetime(2026, 11, 27))
        completed = "2026-11-26T00:00:00Z"
        payload = dict(
            eventId=event_id, eventType="ORDER_COMPLETED", orderId=order_id, customerId=customer_id,
            orderStatus="COMPLETED", paymentStatus="PAID", completedAt=completed, occurredAt=completed,
            items=[dict(orderItemId=line_id, productVariantId=variant_id, quantity=2, purchaseType="CUSTOM_BUNDLE",
                        components=[dict(ingredientId=ingredient, ingredientCode="ING-1", quantity=100, unit="g")])],
        )
        db, interactions = Mock(), Mock()
        db.get.return_value = None
        db.scalars.return_value.all.return_value = []
        self.assertEqual(OrderInboxService(interactions)._purchase(db, row, payload), [])
        interactions.stage_interactions.assert_not_called()
        purchase = db.add.call_args.args[0]
        self.assertIsInstance(purchase, CompletedPurchaseOrder)
        self.assertEqual((purchase.id, purchase.customer_id, purchase.source_event_id), (order_id, customer_id, event_id))
        self.assertEqual(purchase.lines[0]["order_item_id"], line_id)
        self.assertEqual(purchase.lines[0]["components"][0]["ingredient_id"], ingredient)
        self.assertEqual(row.status, "APPLIED")

    def test_empty_report_is_valid_xlsx(self):
        db = Mock()
        db.scalars.return_value.all.return_value = []
        today = datetime(2026, 11, 27)
        with patch("app.service.ingredient_report_workbook.retained_lines", return_value=[]):
            output, evidence = build_ingredient_workbook(db, SimpleNamespace(
                ai_report_max_evidence_rows=20, taste_catalog_timeout_seconds=3,
            ), today.date(), today.date(), today)
        self.assertEqual(evidence["data_status"], "NO_DATA")
        workbook = load_workbook(BytesIO(output), read_only=True)
        self.assertEqual(workbook.sheetnames, [
            "Overview", "Recommendations", "Direct bundle purchases", "Inferred product BOM",
        ])
        self.assertEqual(len(list(workbook["Recommendations"].values)), 1)
        workbook.close()
