from datetime import date, datetime, timedelta
from decimal import Decimal
import hashlib
from io import BytesIO
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch
from uuid import uuid4

from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient
from openpyxl import load_workbook
from sqlalchemy import create_engine, select, text
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from starlette.responses import Response

from app.api.ai_reports import router as report_router, get_business_report_service
from app.api.recommendations import router as recommendation_router, get_recommendation_service
from app.database.session import get_db
from app.kafka.purchase_event_adapter import PurchaseEventAdapter, PurchaseEventValidationError
from app.kafka.purchase_consumer import PurchaseKafkaConsumer
from app.models.business_report import BusinessReportExport, CompletedPurchaseOrder, RecommendationSnapshot
from app.models.interaction_event import InteractionEvent
from app.repositories.interaction_event_repository import InteractionEventRepository
from app.schemas.business_report import BusinessExportRequest
from app.schemas.ai_report import TrustedReportFile
from app.service.business_report_service import BusinessReportService
from app.service.business_report_workbook import SHEETS, attribute, build_workbook, period_bounds
from app.service.interaction_service import InteractionService
from app.service.recommendation_snapshot_service import RecommendationSnapshotService, fingerprint
from app.recommendation.v1_model import V1ModelError
from tests.test_ai_reports import signed as report_signed, A, B, SECRET
from tests.test_recommendation_modes_security import ModeFixture, Mode, signed, settings, CUSTOMER

NOW = datetime(2026, 10, 12, 10)


def snapshot(identifier="s1", when=NOW - timedelta(days=1), customer="c1", products=("a", "b"), mode="DISCOVERY"):
    return RecommendationSnapshot(id=identifier, customer_id=customer, request_key=str(uuid4()), request_hash="hash",
        generated_at=when, observed_at=when, mode=mode, source="POPULARITY", fallback_reason=None,
        evaluation_days=7, context={"kind": "GENERATED_RESPONSE"},
        response={"items": [dict(product_id=p, name="Trà " + p, rank=n) for n, p in enumerate(products, 1)]})


def order(identifier="o1", when=NOW, customer="c1", products=(("a", 3), ("a", 2), ("x", 1))):
    lines = [dict(order_item_id=f"{identifier}-{n}", product_id=p, product_variant_id="v-" + p, quantity=str(q))
             for n, (p, q) in enumerate(products)]
    return CompletedPurchaseOrder(id=identifier, customer_id=customer, source_event_id="event-" + identifier,
        occurred_at=when, observed_at=when, lines=lines, evidence_hash=fingerprint(lines))


class DbFixture:
    def setUp(self):
        self.engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
        for model in (RecommendationSnapshot, CompletedPurchaseOrder, BusinessReportExport):
            model.__table__.create(self.engine)
        self.db = Session(self.engine, expire_on_commit=False)
        self.db.execute(text("CREATE TABLE products(id TEXT PRIMARY KEY, name TEXT)"))
        self.db.commit()
        self.addCleanup(self.engine.dispose); self.addCleanup(self.db.close)


class SnapshotTests(DbFixture, ModeFixture, unittest.TestCase):
    def setUp(self):
        DbFixture.setUp(self); ModeFixture.setUp(self)
        self.recorder = RecommendationSnapshotService()
        self.cfg = settings()
        self.config_patch = patch("app.service.recommendation_snapshot_service.get_settings", return_value=self.cfg)
        self.config_patch.start(); self.addCleanup(self.config_patch.stop)

    def issue(self, key=None, mode=Mode.DISCOVERY, limit=5, customer=CUSTOMER):
        return self.recorder.issue(self.db, self.service, customer, limit, True, mode, key)

    def test_routing_and_modes_are_frozen_without_probabilities(self):
        for count in (0, 1, 2, 5):
            self.history(count, {} if count == 0 else {"a": 3, "c": 1})
            for mode in Mode:
                response = self.issue(mode=mode)
                stored = self.db.get(RecommendationSnapshot, response.recommendation_event_id)
                self.assertEqual(stored.mode, mode.value)
                self.assertEqual(stored.source, "POPULARITY" if count < 2 else "HYBRID")
                self.assertEqual(stored.response["items"], response.model_dump(mode="json")["items"])
                self.assertLessEqual(len(stored.response["items"]), 5)
                self.assertEqual(stored.context["kind"], "GENERATED_RESPONSE")
                self.assertIsNotNone(response.generated_at.tzinfo)

    def test_retry_returns_exact_response_despite_changed_history(self):
        key = str(uuid4()); first = self.issue(key)
        self.history(0, {})
        second = self.issue(key)
        self.assertEqual(first.model_dump(), second.model_dump())
        self.assertEqual(len(self.db.scalars(select(RecommendationSnapshot)).all()), 1)

    def test_retry_with_different_mode_or_limit_conflicts(self):
        key = str(uuid4()); self.issue(key)
        for parameters in (dict(mode=Mode.REPEAT), dict(limit=3)):
            with self.assertRaises(HTTPException) as error: self.issue(key, **parameters)
            self.assertEqual(error.exception.status_code, 409)

    def test_same_customer_distinct_request_keys_record_separate_events(self):
        first, second = self.issue(), self.issue()
        self.assertNotEqual(first.recommendation_event_id, second.recommendation_event_id)

    def test_idempotency_key_is_scoped_to_customer(self):
        key = str(uuid4())
        self.assertNotEqual(self.issue(key).recommendation_event_id, self.issue(key, customer="other").recommendation_event_id)

    def test_fallback_and_missing_history_record_true_source(self):
        self.store.load.side_effect = V1ModelError("fixture missing")
        response = self.issue()
        row = self.db.get(RecommendationSnapshot, response.recommendation_event_id)
        self.assertEqual((row.source, row.fallback_reason), ("FALLBACK", "V1_MODEL_NOT_AVAILABLE"))
        self.history(0, {})
        response = self.issue(mode=Mode.REPEAT)
        self.assertEqual(response.items, [])

    def test_storage_failure_fails_closed_and_logs_without_secrets(self):
        with patch.object(self.db, "commit", side_effect=OperationalError("fixture", {}, Exception("hidden"))):
            with self.assertLogs("app.service.recommendation_snapshot_service", level="ERROR") as logs:
                with self.assertRaises(HTTPException) as error: self.issue()
        self.assertEqual(error.exception.status_code, 503)
        self.assertNotIn("hidden", str(logs.output))
        self.assertEqual(self.db.scalars(select(RecommendationSnapshot)).all(), [])

    def test_authorized_endpoint_records_only_server_products_and_rejects_forgery(self):
        from app.core import recommendation_auth as auth
        app = FastAPI(); app.include_router(recommendation_router)
        app.dependency_overrides[get_db] = lambda: self.db
        app.dependency_overrides[get_recommendation_service] = lambda: self.service
        path = "/recommendations/" + CUSTOMER
        with patch.object(auth, "get_settings", return_value=self.cfg), patch.object(auth, "get_replay_store", return_value=auth.MemoryReplay()), TestClient(app) as client:
            self.assertEqual(client.get(path).status_code, 401)
            self.assertEqual(client.post(path, json={"products": ["forged"]}, headers=signed(path, method="POST")).status_code, 405)
            self.assertEqual(client.get(path, headers=dict(signed(path), **{"Idempotency-Key": "not-uuid"})).status_code, 422)
            key = str(uuid4())
            first = client.get(path, headers=dict(signed(path), **{"Idempotency-Key": key}))
            second = client.get(path, headers=dict(signed(path), **{"Idempotency-Key": key}))
            self.assertEqual(first.status_code, 200)
            self.assertEqual(first.json(), second.json())
            self.assertEqual(len(self.db.scalars(select(RecommendationSnapshot)).all()), 1)

    def test_legacy_include_all_is_preserved_and_explicitly_non_reportable(self):
        response = self.recorder.issue(self.db, self.service, CUSTOMER, 5, False, None)
        row = self.db.get(RecommendationSnapshot, response.recommendation_event_id)
        self.assertEqual(row.mode, "LEGACY_ALL"); self.assertFalse(row.context["reportable"])

    def test_concurrent_same_customer_key_replays_winning_committed_snapshot(self):
        import copy
        commit = self.db.commit
        def concurrent_commit():
            local = next(row for row in self.db.new if isinstance(row, RecommendationSnapshot))
            response = copy.deepcopy(local.response)
            winning_id = str(uuid4()); response["recommendation_event_id"] = winning_id
            with Session(self.engine) as other:
                other.add(RecommendationSnapshot(id=winning_id, customer_id=local.customer_id,
                    request_key=local.request_key, request_hash=local.request_hash, generated_at=local.generated_at,
                    observed_at=local.observed_at, mode=local.mode, source=local.source,
                    fallback_reason=local.fallback_reason, evaluation_days=7, context=local.context, response=response))
                other.commit()
            commit()
        with patch.object(self.db, "commit", side_effect=concurrent_commit):
            result = self.issue(str(uuid4()))
        self.assertEqual(self.db.scalar(select(RecommendationSnapshot)).id, result.recommendation_event_id)
        self.assertEqual(len(self.db.scalars(select(RecommendationSnapshot)).all()), 1)


class AttributionTests(unittest.TestCase):
    def test_strict_timestamp_boundary_and_expiry_inclusive(self):
        s = snapshot()
        for when, expected in ((s.generated_at - timedelta(microseconds=1), "NO_PURCHASE"),
                               (s.generated_at, "NO_PURCHASE"), (s.generated_at + timedelta(microseconds=1), "MATCHED"),
                               (s.generated_at + timedelta(days=7), "MATCHED"),
                               (s.generated_at + timedelta(days=7, microseconds=1), "NO_PURCHASE")):
            with self.subTest(when=when):
                result = attribute([s], [order(when=when)], s.generated_at + timedelta(days=8))[s.id]
                self.assertEqual(result["status"], expected)

    def test_nearest_prior_wins_one_order_and_loser_does_not_fall_forward(self):
        old, latest = snapshot("a", NOW-timedelta(days=2)), snapshot("b")
        orders = [order(), order("second", NOW+timedelta(days=1))]
        result = attribute([old, latest], orders, NOW+timedelta(days=2))
        self.assertEqual(result[old.id]["status"], "SUPERSEDED")
        self.assertIsNone(result[old.id]["matches"])
        self.assertEqual(result[latest.id]["order"].id, "o1")

    def test_equal_snapshot_times_have_stable_tie_break(self):
        result = attribute([snapshot("a"), snapshot("b")], [order()], NOW)
        self.assertEqual(result["b"]["status"], "MATCHED")
        self.assertEqual(result["a"]["winner_id"], "b")

    def test_pending_has_unknown_result_and_expired_has_no_recorded_order(self):
        s = snapshot()
        self.assertIsNone(attribute([s], [], NOW)[s.id]["matches"])
        self.assertEqual(attribute([s], [], NOW+timedelta(days=8))[s.id]["status"], "NO_PURCHASE")

    def test_customer_identity_and_distinct_product_matching(self):
        s = snapshot()
        result = attribute([s], [order(customer="other")], NOW)[s.id]
        self.assertEqual(result["status"], "NOT_YET_EVALUATED")
        result = attribute([s], [order()], NOW)[s.id]
        self.assertEqual(result["matches"], ["a"])

    def test_no_overlap_is_evaluated_and_not_pending(self):
        s = snapshot()
        self.assertEqual(attribute([s], [order(products=(("x", 2),))], NOW)[s.id]["status"], "NO_OVERLAP")

    def test_observation_cutoff_and_future_order_prevent_temporal_leakage(self):
        s, o = snapshot(), order()
        o.observed_at = NOW + timedelta(hours=1)
        self.assertEqual(attribute([s], [o], NOW)[s.id]["status"], "NOT_YET_EVALUATED")
        o = order(when=NOW+timedelta(hours=1))
        self.assertEqual(attribute([s], [o], NOW)[s.id]["status"], "NOT_YET_EVALUATED")

    def test_repeat_mode_remains_separate_and_matches_real_product(self):
        s = snapshot(mode="REPEAT")
        self.assertEqual(attribute([s], [order()], NOW)[s.id]["matches"], ["a"])


class PurchaseIngestionTests(DbFixture, unittest.TestCase):
    def setUp(self):
        super().setUp()
        self.db.execute(text("""CREATE TABLE interaction_events (
            id TEXT PRIMARY KEY, source_event_id TEXT, order_item_id TEXT, return_item_id TEXT,
            customer_id TEXT, product_variant_id TEXT, product_id TEXT, event_type TEXT, quantity NUMERIC,
            unit_price NUMERIC, order_id TEXT, return_id TEXT, return_reason TEXT, event_time DATETIME,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(source_event_id, order_item_id, event_type))""")); self.db.commit()
        resolver = MagicMock()
        resolver.resolve_variant.side_effect = lambda db, product_variant_id, sku: SimpleNamespace(id=product_variant_id, product_id=product_variant_id.split("-")[-1])
        self.interactions = InteractionService(resolver, InteractionEventRepository(), MagicMock())
        self.payload = dict(eventId=str(uuid4()), eventType="ORDER_COMPLETED", orderStatus="COMPLETED", paymentStatus="PAID",
            orderId=str(uuid4()), customerId="c1", occurredAt="2026-10-12T09:00:00Z",
            items=[dict(orderItemId=str(uuid4()), productVariantId="v-a", quantity=3),
                   dict(orderItemId=str(uuid4()), productVariantId="v-a", quantity=2)])

    def consume(self, payload=None):
        value = payload or self.payload
        interactions = PurchaseEventAdapter().to_canonical_interactions(value)
        return self.interactions.process_batch(self.db, interactions, completed_order=value)

    def test_whole_order_and_duplicate_kafka_delivery_are_deduplicated(self):
        self.consume(); self.consume()
        row = self.db.get(CompletedPurchaseOrder, self.payload["orderId"])
        self.assertEqual([line["quantity"] for line in row.lines], ["3", "2"] if row.lines[0]["order_item_id"] == self.payload["items"][0]["orderItemId"] else ["2", "3"])
        self.assertEqual(len(row.lines), 2)
        self.assertEqual(len(self.db.scalars(select(CompletedPurchaseOrder)).all()), 1)
        self.assertEqual(len(self.db.scalars(select(InteractionEvent)).all()), 2)

    def test_same_order_new_transport_event_does_not_duplicate_report_sales(self):
        self.consume(); self.consume(dict(self.payload, eventId=str(uuid4())))
        self.assertEqual(len(self.db.scalars(select(CompletedPurchaseOrder)).all()), 1)
        self.assertEqual(len(self.db.scalars(select(InteractionEvent)).all()), 2)
        self.assertEqual(self.interactions.customer_interaction_repository.apply_interaction.call_count, 2)

    def test_business_completion_time_survives_transport_retry(self):
        self.payload["completedAt"] = "2026-10-12T08:00:00Z"
        first = self.consume()
        self.consume(dict(self.payload, eventId=str(uuid4()), occurredAt="2026-10-12T10:00:00Z"))
        self.assertEqual(first[0].event_time.hour, 8)
        self.assertEqual(len(self.db.scalars(select(InteractionEvent)).all()), 2)

    def test_conflicting_customer_order_price_variant_or_time_rejects_atomically(self):
        self.consume()
        for change in (dict(customerId="other"), dict(orderId=str(uuid4())),
                       dict(occurredAt="2026-10-12T09:00:01Z"),
                       dict(items=[dict(self.payload["items"][0], unitPrice=99), self.payload["items"][1]]),
                       dict(items=[dict(self.payload["items"][0], productVariantId="v-b"), self.payload["items"][1]])):
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.consume(dict(self.payload, eventId=str(uuid4()), **change))
            self.assertEqual(len(self.db.scalars(select(InteractionEvent)).all()), 2)

    def test_unknown_variant_or_contradicting_product_never_writes(self):
        from app.service.product_resolver import ProductResolutionError
        invalid = dict(self.payload, items=[dict(self.payload["items"][0], productId="wrong")])
        with self.assertRaises(ProductResolutionError): self.consume(invalid)
        self.interactions.product_resolver.resolve_variant.side_effect = None
        self.interactions.product_resolver.resolve_variant.return_value = None
        with self.assertRaises(ProductResolutionError): self.consume()
        self.assertEqual(self.db.scalars(select(InteractionEvent)).all(), [])

    def test_invalid_legacy_or_custom_payload_never_commits_offset(self):
        import json
        for change in (dict(items=[]), dict(orderStatus="PAYMENT_CONFIRMED"),
                       dict(items=[dict(self.payload["items"][0], purchaseType="CUSTOM_BOM")])):
            msg = MagicMock(); msg.error.return_value = None
            msg.value.return_value = json.dumps(dict(self.payload, **change)).encode()
            consumer = PurchaseKafkaConsumer(consumer=MagicMock(), interaction_service=self.interactions,
                session_factory=lambda: Session(self.engine, expire_on_commit=False), settings=settings())
            with patch("app.kafka.purchase_consumer.commit_message_offset") as committed:
                with self.assertRaises(PurchaseEventValidationError): consumer.consume_message(msg)
                committed.assert_not_called()
        self.assertEqual(self.db.scalars(select(InteractionEvent)).all(), [])

    def test_conflicting_duplicate_order_rolls_back_whole_batch(self):
        self.consume()
        modified = dict(self.payload, eventId=str(uuid4()), items=[dict(self.payload["items"][0], quantity=99), self.payload["items"][1]])
        with self.assertRaises(ValueError): self.consume(modified)
        self.assertEqual(len(self.db.scalars(select(InteractionEvent)).all()), 2)

    def test_unpaid_cancelled_and_return_events_are_not_purchase_evidence(self):
        for change in (dict(orderStatus="CANCELLED"), dict(paymentStatus="UNPAID"), dict(eventType="RETURN")):
            with self.assertRaises(PurchaseEventValidationError): self.consume(dict(self.payload, **change))
        self.assertEqual(self.db.scalars(select(CompletedPurchaseOrder)).all(), [])

    def test_duplicate_lines_reject_complete_order(self):
        invalid = dict(self.payload, items=[self.payload["items"][0], self.payload["items"][0]])
        with self.assertRaises(ValueError): self.consume(invalid)
        self.assertEqual(self.db.scalars(select(InteractionEvent)).all(), [])

    def test_customerless_sale_preserves_order_without_inventing_identity(self):
        self.consume(dict(self.payload, customerId=None))
        self.assertIsNone(self.db.get(CompletedPurchaseOrder, self.payload["orderId"]).customer_id)

    def test_mutated_redelivery_and_ambiguous_timestamp_are_rejected(self):
        self.consume()
        invalid = dict(self.payload, items=[dict(self.payload["items"][0], quantity=999), self.payload["items"][1]])
        with self.assertRaises(ValueError): self.consume(invalid)
        for change in (dict(occurredAt="2026-10-12T09:00:00"), dict(items=[dict(self.payload["items"][0], quantity="NaN")])):
            with self.assertRaises(PurchaseEventValidationError): self.consume(dict(self.payload, **change))

    def test_real_consumer_commits_offset_only_after_whole_order_transaction(self):
        import json
        msg = MagicMock(); msg.error.return_value = None; msg.value.return_value = json.dumps(self.payload).encode()
        consumer = PurchaseKafkaConsumer(consumer=MagicMock(), interaction_service=self.interactions,
            session_factory=lambda: Session(self.engine, expire_on_commit=False), settings=settings())
        with patch("app.kafka.purchase_consumer.commit_message_offset") as committed:
            consumer.consume_message(msg)
            self.assertIsNotNone(self.db.get(CompletedPurchaseOrder, self.payload["orderId"]))
            committed.assert_called_once()


class WorkbookTests(DbFixture, unittest.TestCase):
    def build(self, rows=(), start=date(2026, 10, 11), end=date(2026, 10, 12)):
        self.db.add_all(rows); self.db.commit()
        data, evidence = build_workbook(self.db, start, end, NOW)
        return load_workbook(BytesIO(data)), data, evidence

    def test_exact_four_sheets_headers_empty_states_and_valid_zip(self):
        wb, data, _ = self.build()
        self.assertEqual(wb.sheetnames, SHEETS)
        self.assertTrue(data.startswith(b"PK"))
        for index, ws in enumerate(wb):
            self.assertEqual(ws.freeze_panes, None if index == 0 else "A4")
            self.assertTrue(any(c.value == "Chưa có dữ liệu" for row in ws for c in row))
        self.assertEqual([c.value for c in wb.worksheets[3][3]],
                         ["Mã lần gợi ý", "Mã KH", "Số món gợi ý", "Số món khách mua", "Số món trùng", "Kết quả"])

    def test_summary_appearances_distinct_customers_orders_units_and_matches(self):
        wb, _, evidence = self.build([snapshot("s1", NOW-timedelta(days=1)), snapshot("s2", NOW-timedelta(hours=1)),
            order(), order("o2", NOW-timedelta(hours=12)), order("guest", customer=None)])
        overview = wb.worksheets[0]
        self.assertEqual([overview[cell].value for cell in ("B4", "B5", "B6", "B7")], [1, 4, 18, 2])
        product_rows = list(wb.worksheets[2].values)[3:]
        tea_a = next(r for r in product_rows if r[1] == "Trà a")
        self.assertEqual(tea_a[2:], (1, 2, 3, 15))
        match_rows = list(wb.worksheets[3].values)[3:]
        self.assertEqual([r[3:5] for r in match_rows], [(2, 1), (2, 1)])
        self.assertEqual(len(evidence["sales"]), 3)

    def test_pending_and_superseded_cells_are_blank_not_zero(self):
        wb, _, _ = self.build([snapshot("old", NOW-timedelta(hours=3)), snapshot("winner", NOW-timedelta(hours=2)),
            snapshot("pending", NOW-timedelta(hours=1), customer="other"), order()])
        rows = {r[0]: r for r in list(wb.worksheets[3].values)[3:]}
        self.assertEqual(rows["pending"][3:5], (None, None))
        self.assertEqual(rows["old"][3:5], (None, None))
        self.assertEqual(rows["winner"][4], 1)

    def test_local_calendar_boundaries_and_next_order_after_period(self):
        start, end = period_bounds(date(2026, 10, 11), date(2026, 10, 11))
        self.assertEqual(start, datetime(2026, 10, 10, 17))
        wb, _, evidence = self.build([snapshot("in", start), snapshot("out", end),
            order("inside", start+timedelta(hours=1)), order("outside", end)], end=date(2026, 10, 11))
        self.assertEqual(len(evidence["snapshots"]), 1)
        self.assertEqual(evidence["sales"][0]["order_id"], "inside")

    def test_competing_snapshot_outside_period_prevents_double_attribution(self):
        start, end = period_bounds(date(2026, 10, 11), date(2026, 10, 11))
        wb, _, evidence = self.build([snapshot("in", end-timedelta(hours=1)), snapshot("outside", end),
            order("after", end+timedelta(hours=1))], end=date(2026, 10, 11))
        self.assertEqual(evidence["snapshots"][0]["status"], "SUPERSEDED")

    def test_literal_formula_text_privacy_and_no_benchmark_or_probability_columns(self):
        s = snapshot(); s.response["items"][0]["name"] = '=HYPERLINK("bad")'
        wb, _, _ = self.build([s, order()])
        cells = [c for ws in wb for row in ws for c in row]
        self.assertFalse(any(c.data_type == "f" for c in cells))
        text_values = " ".join(str(c.value) for c in cells)
        self.assertNotIn("Recall@5", text_values); self.assertNotIn("60%", text_values)
        self.assertNotIn("c1", text_values)
        self.assertTrue(wb.worksheets[1]["A4"].comment.text.startswith("Mã lần gợi ý:"))

    def test_fractional_cups_are_rejected_instead_of_rounded(self):
        with self.assertRaises(HTTPException): self.build([order(products=(("a", "1.5"),))])

    def test_limited_report_rejects_without_truncation(self):
        self.db.add_all([snapshot("one"), snapshot("two")]); self.db.commit()
        with self.assertRaises(HTTPException): build_workbook(self.db, date(2026, 10, 11), date(2026, 10, 12), NOW, max_rows=1)


class GeneratedFiles:
    def __init__(self): self.records = {}; self.fail = False
    def store_generated(self, record):
        previous = self.records.setdefault(record.id, (record.created_by, record.workbook, record.sha256))
        assert previous == (record.created_by, record.workbook, record.sha256)
        if self.fail: raise HTTPException(502, "Synthetic lost response after remote commit")
        return TrustedReportFile(fileId=record.id, ownerId=record.created_by, fileName=record.file_name,
            fileType="XLSX", contentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            sizeBytes=len(record.workbook), visibility="PRIVATE", status="ACTIVE", deleted=False)
    def download(self, report, generated=False):
        assert generated
        return Response(self.records[report.id][1], media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        headers={"Cache-Control": "private, no-store"})


class ExportTests(DbFixture, unittest.TestCase):
    def setUp(self):
        super().setUp(); self.files = GeneratedFiles(); self.service = BusinessReportService(self.files)
        self.app = FastAPI(); self.app.include_router(report_router)
        self.app.dependency_overrides[get_db] = lambda: self.db
        self.app.dependency_overrides[get_business_report_service] = lambda: self.service
        auth = patch("app.core.ai_report_auth.get_settings", return_value=SimpleNamespace(internal_header_secret=SECRET))
        cfg = patch("app.service.business_report_service.get_settings", return_value=settings(ai_report_data_source="OPERATIONAL"))
        auth.start(); cfg.start(); self.addCleanup(auth.stop); self.addCleanup(cfg.stop)
        self.client = TestClient(self.app); self.addCleanup(self.client.close)
        self.payload = dict(fromDate="2026-10-11", toDate="2026-10-12", requestId=str(uuid4()))
        self.base = "/api/recommendation/ai-reports/exports"

    def export(self, payload=None, headers=None):
        return self.client.post(self.base, json=payload or self.payload, headers=headers or report_signed())

    def test_create_retry_private_download_and_staff_sharing(self):
        created = self.export(); self.assertEqual(created.status_code, 200)
        self.assertEqual(created.json(), self.export().json())
        record = created.json()
        download = self.client.get(self.base + "/" + record["id"] + "/download", headers=report_signed(B))
        self.assertEqual(download.status_code, 200)
        self.assertEqual(hashlib.sha256(download.content).hexdigest(), record["sha256"])
        self.assertEqual(len(self.db.scalars(select(BusinessReportExport)).all()), 1)
        self.assertEqual(len(self.files.records), 1)
        self.assertEqual(load_workbook(BytesIO(download.content)).sheetnames, SHEETS)

    def test_all_roles_authorized_customer_and_unsigned_denied_every_access(self):
        from app.core.ai_report_auth import AI_REPORT_ROLES
        record = self.export().json()
        for role in AI_REPORT_ROLES:
            self.assertEqual(self.client.get(self.base+"/"+record["id"], headers=report_signed(roles=role)).status_code, 200)
        for method, path, payload in [("POST", self.base, self.payload), ("GET", self.base+"/"+record["id"], None),
                                      ("GET", self.base+"/"+record["id"]+"/download", None)]:
            self.assertEqual(self.client.request(method, path, json=payload).status_code, 401)
            self.assertEqual(self.client.request(method, path, json=payload, headers=report_signed(roles="CUSTOMER")).status_code, 403)

    def test_period_conflict_future_range_and_unknown_fields_rejected(self):
        self.export()
        self.assertEqual(self.export(dict(self.payload, toDate="2026-10-11")).status_code, 409)
        for payload in (dict(self.payload, fromDate="2026-10-13"), dict(self.payload, toDate="2099-01-01"),
                        dict(self.payload, fromDate="2024-01-01"), dict(self.payload, ownerId=B)):
            self.assertEqual(self.export(payload).status_code, 422)

    def test_lost_remote_response_reuses_frozen_bytes_after_new_data_arrives(self):
        self.files.fail = True
        self.assertEqual(self.export().status_code, 502)
        frozen = self.db.scalar(select(BusinessReportExport)).workbook
        self.db.add(snapshot()); self.db.commit()
        self.files.fail = False
        self.assertEqual(self.export().status_code, 200)
        self.assertEqual(self.db.scalar(select(BusinessReportExport)).workbook, frozen)
        self.assertEqual(len(self.files.records), 1)

    def test_remote_success_local_commit_failure_recovers_same_file(self):
        commit = self.db.commit; count = 0
        def fail_third():
            nonlocal count
            count += 1
            if count == 3: raise RuntimeError("Synthetic local finalization failure")
            commit()
        with patch.object(self.db, "commit", side_effect=fail_third):
            with self.assertRaises(RuntimeError): self.service.export(self.db, BusinessExportRequest(**self.payload), A)
        self.assertEqual(self.export().status_code, 200)
        self.assertEqual(len(self.files.records), 1)

    def test_request_key_is_scoped_to_actor(self):
        first = self.export().json(); other = self.export(headers=report_signed(B)).json()
        self.assertNotEqual(first["id"], other["id"])

    def test_preparation_lock_refreshes_stale_orm_state_from_concurrent_request(self):
        export_id = str(uuid4())
        cached = BusinessReportExport(id=export_id, created_by=A, request_key=self.payload["requestId"],
            from_date=date(2026, 10, 11), to_date=date(2026, 10, 12), generated_at=NOW, status="PENDING",
            file_name="AI_Report_HuongVanTra_20261011_20261012.xlsx")
        self.db.add(cached); self.db.commit()
        workbook, evidence = build_workbook(self.db, cached.from_date, cached.to_date, NOW)
        with Session(self.engine) as other:
            concurrent = other.get(BusinessReportExport, export_id)
            concurrent.workbook, concurrent.evidence = workbook, evidence
            concurrent.sha256, concurrent.status = hashlib.sha256(workbook).hexdigest(), "PREPARED"
            other.commit()
        self.assertIsNone(cached.workbook)
        with patch("app.service.business_report_service.build_workbook", side_effect=AssertionError("Must reuse winning bytes")):
            result = self.export()
        self.assertEqual(result.status_code, 200)
        self.assertEqual(self.files.records[export_id][1], workbook)


if __name__ == "__main__": unittest.main()
