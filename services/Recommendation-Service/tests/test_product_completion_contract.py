"""Shared producer fixture and fail-closed PRODUCT boundary; no broker or DB."""
from copy import deepcopy
import json
from pathlib import Path
import unittest
from unittest.mock import MagicMock

from app.kafka.purchase_event_adapter import PurchaseEventAdapter, PurchaseEventValidationError
from app.service.product_resolver import ProductResolver
from app.service.product_resolver import ProductResolutionError
from app.service.interaction_service import InteractionService


FIXTURE = (Path(__file__).resolve().parents[2] / "Order-Service/src/test/resources/contracts/product-completed-v1.json")


class ProductCompletionAdapterTests(unittest.TestCase):
    def setUp(self):
        self.payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.adapter = PurchaseEventAdapter()

    def test_shared_producer_fixture_uses_business_time_and_exact_ids(self):
        line, = self.adapter.to_canonical_interactions(self.payload)
        self.assertEqual(line.event_time.isoformat(), "2026-10-01T08:00:00+00:00")
        self.assertEqual(line.product_variant_id, self.payload["items"][0]["productVariantId"])
        self.assertIsNone(line.product_id)  # Order has no persisted authoritative Product binding.
        self.assertEqual(line.order_item_id, self.payload["items"][0]["orderItemId"])

    def test_invalid_statuses_versions_sources_and_missing_fields(self):
        for key, value in [("orderStatus", s) for s in ("PAYMENT_CONFIRMED", "PENDING_PAYMENT", "CANCELLED", "EXPIRED", "PAYMENT_FAILED")]+[
            ("paymentStatus", "REFUNDED"), ("paymentStatus", "PARTIALLY_REFUNDED"),
            ("eventVersion", 2), ("eventVersion", True), ("sourceService", "unknown"),
            ("items", []), ("items", None), ("completedAt", None),
            ("completedAt", "2026-10-01T09:00:00Z"), ("completedAt", "2026-10-01T08:00:00"),
        ]:
            with self.subTest(key=key, value=value), self.assertRaises(PurchaseEventValidationError):
                self.adapter.to_canonical_interactions(dict(self.payload, **{key: value}))
        for key in ("items", "customerId", "orderId", "eventId"):
            payload = deepcopy(self.payload); payload.pop(key)
            with self.subTest(missing=key), self.assertRaises(PurchaseEventValidationError):
                self.adapter.to_canonical_interactions(payload)

    def test_invalid_line_identity_quantity_and_custom_discriminator(self):
        for key, value in [("quantity", q) for q in (0, -1, "NaN", "Infinity", "invalid", "0.0001", "100000000000")]+[
            ("orderItemId", None), ("productVariantId", None),
            ("purchaseType", "CUSTOM_BOM"), ("itemType", "CUSTOM_BUNDLE"),
            ("unitPrice", "NaN"), ("unitPrice", -1),
        ]:
            payload = deepcopy(self.payload); payload["items"][0][key] = value
            with self.subTest(key=key, value=value), self.assertRaises(PurchaseEventValidationError):
                self.adapter.to_canonical_interactions(payload)

    def test_legacy_complete_v1_remains_readable_and_guest_is_explicit(self):
        for key in ("eventVersion", "sourceService", "completedAt"):
            self.payload.pop(key)
        self.payload["items"][0].pop("purchaseType")
        self.payload["customerId"] = None
        line, = self.adapter.to_canonical_interactions(self.payload)
        self.assertIsNone(line.customer_id)
        self.assertEqual(line.event_time.second, 1)

    def test_unknown_explicit_variant_never_falls_back_to_known_sku(self):
        repository = MagicMock(); repository.find_by_id.return_value = None
        resolver = ProductResolver(repository)
        service = InteractionService(resolver, MagicMock(), MagicMock())
        interaction, = self.adapter.to_canonical_interactions(self.payload)
        with self.assertRaises(ProductResolutionError):
            service._process_one(MagicMock(), interaction)
        repository.find_by_sku.assert_not_called()

    def test_eventrouter_headers_enforce_version_and_identity_consistency(self):
        self.adapter.validate_headers(self.payload, [("event_version", b"1"), ("source_service", b"order-service")])
        for headers in ([("event_version", b"2")], [("event_id", b"different")],
                        [("event_version", b"1"), ("event_version", b"2")],
                        [("source_service", b"unknown")]):
            with self.subTest(headers=headers), self.assertRaises(PurchaseEventValidationError):
                self.adapter.validate_headers(self.payload, headers)
