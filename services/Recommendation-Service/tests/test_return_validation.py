"""RETURN contract and mocked transaction/offset boundaries; no broker or DB."""
from copy import deepcopy
from datetime import datetime, timezone
from decimal import Decimal, localcontext
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

from app.kafka.purchase_consumer import PurchaseKafkaConsumer
from app.kafka.purchase_event_adapter import PurchaseEventValidationError
from app.kafka.return_consumer import ReturnKafkaConsumer
from app.kafka.return_event_adapter import ReturnEventAdapter, ReturnEventValidationError
from app.kafka.worker import KafkaWorker
from app.service.interaction_service import InteractionService


EVENT = "550e8400-e29b-41d4-a716-446655440001"
RETURN = "550e8400-e29b-41d4-a716-446655440002"
ORDER = "550e8400-e29b-41d4-a716-446655440003"
CUSTOMER = "550e8400-e29b-41d4-a716-446655440004"
RETURN_LINE = "550e8400-e29b-41d4-a716-446655440101"
ORDER_LINE = "550e8400-e29b-41d4-a716-446655440201"
VARIANT = "550e8400-e29b-41d4-a716-446655440301"
TOPIC = "notification.return.completed"
PURCHASE_FIXTURE = (Path(__file__).resolve().parents[2]
                    / "Order-Service/src/test/resources/contracts/product-completed-v1.json")


def payload():
    # The producer's RETURN body has no eventVersion/sourceService/completedAt.
    return {
        "eventId": EVENT, "eventType": "RETURN_COMPLETED", "eventKey": RETURN,
        "returnId": RETURN, "returnCode": "RET-00001", "orderId": ORDER,
        "orderCode": "ORD-00001", "branchId": None, "customerId": CUSTOMER,
        "userId": None, "actorUserId": None, "paymentId": None,
        "refundTransactionId": None, "oldStatus": "RECEIVED", "newStatus": "COMPLETED",
        "title": "Return completed", "message": "Return completed",
        "sagaId": None, "correlationId": RETURN, "causationId": None,
        "occurredAt": "2026-11-06T08:30:00+07:00",
        "items": [{
            "returnItemId": RETURN_LINE, "orderItemId": ORDER_LINE,
            "productVariantId": VARIANT, "sku": "TRA-HOP-00001",
            "productName": "Tea", "quantity": "2.500",
            "returnReason": "CHANGE_OF_MIND", "condition": "SEALED", "note": None,
        }],
    }


def two_lines():
    value = payload()
    second = dict(value["items"][0],
                  returnItemId="550e8400-e29b-41d4-a716-446655440102",
                  orderItemId="550e8400-e29b-41d4-a716-446655440202")
    value["items"].append(second)
    return value


def headers(value):
    return [
        ("event_id", value["eventId"].encode()), ("event_type", b"RETURN_COMPLETED"),
        ("event_version", b"1"), ("source_service", b"order-service"),
        ("aggregate_type", b"ORDER_RETURN"), ("aggregate_id", value["returnId"].encode()),
        ("business_key", (value["returnId"] + ":return-completed").encode()),
        ("saga_id", None), ("correlation_id", value["correlationId"].encode()),
        ("causation_id", None),
    ]


def message(value, supplied_headers=None, *, topic=TOPIC, partition=0, offset=14):
    msg = MagicMock()
    msg.error.return_value = None
    msg.value.return_value = json.dumps(value).encode()
    msg.headers.return_value = supplied_headers
    msg.topic.return_value = topic
    msg.partition.return_value = partition
    msg.offset.return_value = offset
    return msg


def committed(topic=TOPIC, partition=0, offset=15):
    return [SimpleNamespace(topic=topic, partition=partition, offset=offset, error=None)]


class ReturnAdapterTests(unittest.TestCase):
    def setUp(self):
        self.adapter = ReturnEventAdapter()

    def test_valid_single_line_preserves_return_semantics(self):
        source = payload()
        before = deepcopy(source)
        line, = self.adapter.to_canonical_interactions(source)
        self.assertEqual((line.source_event_id, line.return_id, line.order_id), (EVENT, RETURN, ORDER))
        self.assertEqual((line.return_item_id, line.order_item_id, line.product_variant_id),
                         (RETURN_LINE, ORDER_LINE, VARIANT))
        self.assertEqual((line.event_type, line.return_reason, line.quantity),
                         ("RETURN", "CHANGE_OF_MIND", Decimal("2.500")))
        self.assertEqual(source, before)

    def test_valid_multiple_distinct_lines_preserve_same_variant(self):
        rows = self.adapter.to_canonical_interactions(two_lines())
        self.assertEqual(len(rows), 2)
        self.assertEqual(len({r.return_item_id for r in rows}), 2)
        self.assertEqual(len({r.order_item_id for r in rows}), 2)
        self.assertEqual({r.product_variant_id for r in rows}, {VARIANT})

    def test_quantity_minimum_maximum_and_exact_values_without_rounding(self):
        for quantity in ("0.001", "99999999999.999", "1E+10", "3", Decimal("2.500")):
            with self.subTest(quantity=str(quantity)):
                source = payload()
                source["items"][0]["quantity"] = quantity
                # Validation must not depend on quantize rounding/context precision.
                with localcontext() as context:
                    context.prec = 2
                    line, = self.adapter.to_canonical_interactions(source)
                self.assertEqual(line.quantity, Decimal(str(quantity)))

    def test_reject_nonpositive_nonfinite_and_malformed_quantities(self):
        for quantity in (0, -1, "NaN", "sNaN", "Infinity", "-Infinity",
                         float("nan"), float("inf"), True, False, "abc", [], {}, None):
            with self.subTest(quantity=repr(quantity)):
                source = payload()
                source["items"][0]["quantity"] = quantity
                with self.assertRaises(ReturnEventValidationError):
                    self.adapter.to_canonical_interactions(source)

    def test_reject_precision_scale_and_exponent_overflow(self):
        for quantity in ("100000000000", "1E+11", "99999999999.9999", "0.0001",
                         "1.0000", "1E-999999", "1E+999999"):
            with self.subTest(quantity=quantity):
                source = payload()
                source["items"][0]["quantity"] = quantity
                with self.assertRaisesRegex(ReturnEventValidationError, "DECIMAL\\(14,3\\)"):
                    self.adapter.to_canonical_interactions(source)

    def test_reject_missing_quantity(self):
        source = payload()
        source["items"][0].pop("quantity")
        with self.assertRaises(ReturnEventValidationError):
            self.adapter.to_canonical_interactions(source)

    def test_timezone_offset_is_normalized_without_changing_instant(self):
        expected = datetime(2026, 11, 6, 1, 30, tzinfo=timezone.utc)
        for stamp in ("2026-11-06T08:30:00+07:00", "2026-11-05T20:30:00-05:00",
                      "2026-11-06T01:30:00Z", datetime.fromisoformat("2026-11-06T08:30:00+07:00")):
            with self.subTest(timestamp=str(stamp)):
                line, = self.adapter.to_canonical_interactions(dict(payload(), occurredAt=stamp))
                self.assertEqual(line.event_time, expected)
                self.assertIs(line.event_time.tzinfo, timezone.utc)

    def test_numeric_seconds_and_milliseconds_preserve_existing_convention(self):
        instant = datetime(2026, 11, 6, 1, 30, tzinfo=timezone.utc)
        for stamp in (int(instant.timestamp()), int(instant.timestamp() * 1000)):
            with self.subTest(timestamp=stamp):
                line, = self.adapter.to_canonical_interactions(dict(payload(), occurredAt=stamp))
                self.assertEqual(line.event_time, instant)

    def test_reject_ambiguous_naive_timestamp(self):
        for stamp in ("2026-11-06T08:30:00", "2026-11-06", datetime(2026, 11, 6, 8, 30)):
            with self.subTest(timestamp=str(stamp)):
                with self.assertRaisesRegex(ReturnEventValidationError, "timezone"):
                    self.adapter.to_canonical_interactions(dict(payload(), occurredAt=stamp))

    def test_reject_invalid_boolean_nonfinite_or_out_of_range_timestamp(self):
        for stamp in (None, "", "not-a-time", True, False, float("nan"), float("inf"),
                      1e300, {}, [], "9999-12-31T23:00:00-02:00"):
            with self.subTest(timestamp=repr(stamp)):
                with self.assertRaises(ReturnEventValidationError):
                    self.adapter.to_canonical_interactions(dict(payload(), occurredAt=stamp))

    def test_reject_missing_or_malformed_required_envelope_identities(self):
        for field in ("eventId", "returnId", "orderId"):
            for identity in (None, " ", True, 123, [], {}, "not-a-uuid", EVENT.replace("-", "")):
                with self.subTest(field=field, identity=repr(identity)):
                    source = dict(payload(), **{field: identity})
                    with self.assertRaises(ReturnEventValidationError):
                        self.adapter.to_canonical_interactions(source)
            source = payload()
            source.pop(field)
            with self.subTest(missing=field), self.assertRaises(ReturnEventValidationError):
                self.adapter.to_canonical_interactions(source)

    def test_reject_malformed_line_identities_even_with_known_sku(self):
        for field in ("returnItemId", "orderItemId", "productVariantId"):
            for identity in ("", True, 123, [], {}, "not-a-uuid"):
                with self.subTest(field=field, identity=repr(identity)):
                    source = payload()
                    source["items"][0][field] = identity
                    with self.assertRaises(ReturnEventValidationError):
                        self.adapter.to_canonical_interactions(source)

    def test_reject_missing_required_line_identities(self):
        for field in ("returnItemId", "orderItemId"):
            source = payload()
            source["items"][0].pop(field)
            with self.subTest(field=field), self.assertRaises(ReturnEventValidationError):
                self.adapter.to_canonical_interactions(source)

    def test_guest_legacy_missing_customer_remains_valid_but_explicit_invalid_customer_rejects(self):
        for remove in (False, True):
            source = dict(payload(), customerId=None)
            if remove:
                source.pop("customerId")
            with self.subTest(missing=remove):
                line, = self.adapter.to_canonical_interactions(source)
                self.assertIsNone(line.customer_id)
        for identity in (" ", False, 123, {}, "customer-name"):
            with self.subTest(identity=repr(identity)), self.assertRaises(ReturnEventValidationError):
                self.adapter.to_canonical_interactions(dict(payload(), customerId=identity))

    def test_sku_only_legacy_lookup_and_exact_variant_are_not_replaced(self):
        for remove in (False, True):
            source = payload()
            source["items"][0]["productVariantId"] = None
            if remove:
                source["items"][0].pop("productVariantId")
            with self.subTest(missing=remove):
                line, = self.adapter.to_canonical_interactions(source)
                self.assertIsNone(line.product_variant_id)
                self.assertEqual(line.sku, "TRA-HOP-00001")
        source = payload()
        source["items"][0].pop("sku")
        line, = self.adapter.to_canonical_interactions(source)
        self.assertEqual(line.product_variant_id, VARIANT)

    def test_reject_missing_all_product_identity_and_invalid_sku(self):
        source = payload()
        source["items"][0].pop("sku")
        source["items"][0].pop("productVariantId")
        with self.assertRaises(ReturnEventValidationError):
            self.adapter.to_canonical_interactions(source)
        for sku in (" ", True, 123, [], "S" * 101):
            source = payload()
            source["items"][0]["sku"] = sku
            with self.subTest(sku=repr(sku)), self.assertRaises(ReturnEventValidationError):
                self.adapter.to_canonical_interactions(source)

    def test_duplicate_return_item_rejects_entire_payload(self):
        source = two_lines()
        source["items"][1]["returnItemId"] = RETURN_LINE
        with self.assertRaisesRegex(ReturnEventValidationError, "Duplicate returnItemId"):
            self.adapter.to_canonical_interactions(source)

    def test_duplicate_order_item_rejects_even_if_operational_line_is_filtered(self):
        source = two_lines()
        source["items"][0]["returnReason"] = "DEFECTIVE"
        source["items"][1]["orderItemId"] = ORDER_LINE
        with self.assertRaisesRegex(ReturnEventValidationError, "Duplicate orderItemId"):
            self.adapter.to_canonical_interactions(source)

    def test_duplicate_identity_normalization_cannot_hide_same_line(self):
        source = two_lines()
        source["items"][1]["returnItemId"] = " " + RETURN_LINE.upper() + " "
        with self.assertRaisesRegex(ReturnEventValidationError, "Duplicate returnItemId"):
            self.adapter.to_canonical_interactions(source)

    def test_multiple_events_may_return_the_same_order_line(self):
        first = payload()
        second = dict(payload(), eventId="550e8400-e29b-41d4-a716-446655440005",
                      returnId="550e8400-e29b-41d4-a716-446655440006")
        second["items"][0]["returnItemId"] = "550e8400-e29b-41d4-a716-446655440102"
        left, = self.adapter.to_canonical_interactions(first)
        right, = self.adapter.to_canonical_interactions(second)
        self.assertEqual(left.order_item_id, right.order_item_id)
        self.assertNotEqual(left.return_item_id, right.return_item_id)

    def test_operational_reasons_never_become_negative_preference(self):
        for reason in ("DEFECTIVE", "DAMAGED", "WRONG_ITEM", "QUALITY_ISSUE", "EXPIRED", "OTHER"):
            source = payload()
            source["items"][0]["returnReason"] = reason
            with self.subTest(reason=reason):
                self.assertEqual(self.adapter.to_canonical_interactions(source), [])

    def test_mixed_reasons_keep_only_change_of_mind_with_positive_return_quantity(self):
        source = two_lines()
        source["items"][0]["returnReason"] = "DEFECTIVE"
        source["items"][1]["returnReason"] = " change_of_mind "
        line, = self.adapter.to_canonical_interactions(source)
        self.assertEqual(line.return_item_id, source["items"][1]["returnItemId"])
        self.assertEqual((line.return_reason, line.quantity), ("CHANGE_OF_MIND", Decimal("2.500")))

    def test_reject_bad_shape_status_type_or_reason(self):
        for source in ([], None, {}, dict(payload(), eventType="ORDER_COMPLETED"),
                       dict(payload(), newStatus="RECEIVED"), dict(payload(), items=[]),
                       dict(payload(), items=[None])):
            with self.subTest(payload=repr(source)), self.assertRaises(ReturnEventValidationError):
                self.adapter.to_canonical_interactions(source)
        for reason in (None, " ", True, {}, "UNKNOWN_REASON"):
            source = payload()
            source["items"][0]["returnReason"] = reason
            with self.subTest(reason=repr(reason)), self.assertRaises(ReturnEventValidationError):
                self.adapter.to_canonical_interactions(source)

    def test_operational_return_is_still_fully_validated(self):
        source = payload()
        source["items"][0].update(returnReason="DEFECTIVE", quantity="Infinity")
        with self.assertRaises(ReturnEventValidationError):
            self.adapter.to_canonical_interactions(source)


class ReturnHeaderTests(unittest.TestCase):
    def test_real_eventrouter_headers_match_producer_body_without_purchase_only_fields(self):
        source = payload()
        self.assertNotIn("eventVersion", source)
        self.assertNotIn("sourceService", source)
        ReturnEventAdapter.validate_headers(source, headers(source))
        self.assertEqual(len(ReturnEventAdapter().to_canonical_interactions(source)), 1)

    def test_legacy_missing_empty_partial_and_unrelated_headers_remain_valid(self):
        for supplied in (None, [], [("event_version", b"1")], [("traceparent", b"trace")]):
            with self.subTest(headers=supplied):
                ReturnEventAdapter.validate_headers(payload(), supplied)

    def test_explicit_supported_body_version_source_and_string_headers(self):
        source = dict(payload(), eventVersion=1, sourceService="order-service")
        supplied = [(k, v.decode() if isinstance(v, bytes) else v) for k, v in headers(source)]
        ReturnEventAdapter.validate_headers(source, supplied)
        self.assertEqual(len(ReturnEventAdapter().to_canonical_interactions(source)), 1)

    def test_reject_unsupported_body_version_and_source_without_headers(self):
        for field, value in (("eventVersion", 2), ("eventVersion", True), ("eventVersion", "1"),
                             ("eventVersion", 1.0), ("eventVersion", None),
                             ("sourceService", "inventory-service"), ("sourceService", None)):
            with self.subTest(field=field, value=value), self.assertRaises(ReturnEventValidationError):
                ReturnEventAdapter().to_canonical_interactions(dict(payload(), **{field: value}))

    def test_reject_each_mismatching_emitted_contract_header(self):
        source = payload()
        for key, _ in headers(source):
            with self.subTest(header=key), self.assertRaises(ReturnEventValidationError):
                ReturnEventAdapter.validate_headers(source, [(key, b"contradiction")])

    def test_header_contract_rejects_missing_or_contradictory_body_event_type(self):
        for body_type in (None, "ORDER_COMPLETED"):
            for header_type in (b"RETURN_COMPLETED", b"ORDER_COMPLETED"):
                with self.subTest(body=body_type, header=header_type):
                    with self.assertRaises(ReturnEventValidationError):
                        ReturnEventAdapter.validate_headers(dict(payload(), eventType=body_type),
                                                            [("event_type", header_type)])

    def test_nullable_trace_headers_match_nonnull_body_when_supplied(self):
        source = dict(payload(), sagaId=ORDER, causationId=CUSTOMER)
        ReturnEventAdapter.validate_headers(source, [("saga_id", ORDER.encode()),
                                                     ("causation_id", CUSTOMER.encode())])

    def test_reject_duplicate_known_headers_including_identical_values(self):
        for key, value in headers(payload()):
            with self.subTest(header=key), self.assertRaisesRegex(ReturnEventValidationError, "Duplicate"):
                ReturnEventAdapter.validate_headers(payload(), [(key, value), (key, value)])

    def test_reject_invalid_encoding_type_null_core_and_malformed_headers(self):
        for supplied in ([("event_id", b"\xff")], [("event_version", 1)], [("event_id", None)],
                         "headers", {"event_id": EVENT}, [("event_id",)], [None], [(123, b"value")]):
            with self.subTest(headers=repr(supplied)), self.assertRaises(ReturnEventValidationError):
                ReturnEventAdapter.validate_headers(payload(), supplied)


class ReturnConsumerSafetyTests(unittest.TestCase):
    def setUp(self):
        self.db = MagicMock()
        self.factory = MagicMock(return_value=self.db)
        self.kafka = MagicMock()
        self.kafka.commit.return_value = committed()
        self.service = MagicMock()
        self.service.process_batch.return_value = [SimpleNamespace(id=RETURN_LINE)]
        self.consumer = ReturnKafkaConsumer(consumer=self.kafka, interaction_service=self.service,
                                           session_factory=self.factory, settings=SimpleNamespace())

    def assert_no_db_or_ack(self):
        self.factory.assert_not_called()
        self.service.process_batch.assert_not_called()
        self.db.assert_not_called()
        self.assertEqual(self.db.mock_calls, [])
        self.kafka.commit.assert_not_called()

    def test_invalid_payload_matrix_never_opens_session_writes_or_acks(self):
        invalid = []
        for quantity in (0, -1, "NaN", "Infinity", "100000000000", "0.0001"):
            source = payload()
            source["items"][0]["quantity"] = quantity
            invalid.append(source)
        invalid.extend([dict(payload(), occurredAt="2026-11-06T08:30:00"),
                        dict(payload(), returnId="not-a-uuid"), dict(payload(), eventVersion=2)])
        for source in invalid:
            with self.subTest(payload=source), self.assertRaises(ReturnEventValidationError):
                self.consumer.consume_message(message(source))
            self.assert_no_db_or_ack()

    def test_invalid_header_and_operational_header_mismatch_cannot_ack(self):
        for reason in ("CHANGE_OF_MIND", "DEFECTIVE"):
            source = payload()
            source["items"][0]["returnReason"] = reason
            with self.subTest(reason=reason), self.assertRaises(ReturnEventValidationError):
                self.consumer.consume_message(message(source, [("event_id", b"wrong")]))
            self.assert_no_db_or_ack()

    def test_valid_first_line_then_invalid_or_duplicate_second_line_never_opens_db(self):
        for change in ({"quantity": "0.0001"}, {"returnItemId": RETURN_LINE},
                       {"orderItemId": ORDER_LINE}):
            source = two_lines()
            source["items"][1].update(change)
            with self.subTest(change=change), self.assertRaises(ReturnEventValidationError):
                self.consumer.consume_message(message(source, headers(source)))
            self.assert_no_db_or_ack()

    def test_valid_multiple_lines_are_one_batch_before_synchronous_offset_commit(self):
        source = two_lines()
        msg = message(source, headers(source))
        order = MagicMock()
        order.attach_mock(self.service.process_batch, "batch")
        order.attach_mock(self.db.close, "close")
        order.attach_mock(self.kafka.commit, "ack")
        result = self.consumer.consume_message(msg)
        self.assertEqual(result, self.service.process_batch.return_value)
        self.factory.assert_called_once_with()
        self.service.process_batch.assert_called_once()
        db, rows = self.service.process_batch.call_args.args
        self.assertIs(db, self.db)
        self.assertEqual(len(rows), 2)
        self.assertEqual([c[0] for c in order.mock_calls], ["batch", "close", "ack"])
        self.kafka.commit.assert_called_once_with(message=msg, asynchronous=False)

    def test_legacy_missing_header_or_header_method_remains_accepted(self):
        for remove_method in (False, True):
            self.kafka.reset_mock()
            msg = message(payload())
            if remove_method:
                del msg.headers
            with self.subTest(missing_method=remove_method):
                self.consumer.consume_message(msg)
                self.kafka.commit.assert_called_once_with(message=msg, asynchronous=False)

    def test_operational_noop_is_validated_and_acked_without_db(self):
        source = payload()
        source["items"][0]["returnReason"] = "DAMAGED"
        self.assertEqual(self.consumer.consume_message(message(source, headers(source))), [])
        self.factory.assert_not_called()
        self.service.process_batch.assert_not_called()
        self.kafka.commit.assert_called_once()

    def test_real_batch_boundary_commits_all_lines_before_ack(self):
        service = InteractionService(MagicMock(), MagicMock(), MagicMock())
        events = [SimpleNamespace(id=RETURN_LINE), SimpleNamespace(id=ORDER_LINE)]
        self.consumer.interaction_service = service
        order = MagicMock()
        order.attach_mock(self.db.commit, "db_commit")
        order.attach_mock(self.kafka.commit, "ack")
        with patch.object(service, "_process_one", side_effect=events) as process:
            result = self.consumer.consume_message(message(two_lines()))
        self.assertEqual(result, events)
        self.assertEqual(process.call_count, 2)
        self.assertEqual([c[0] for c in order.mock_calls], ["db_commit", "ack"])
        self.db.rollback.assert_not_called()

    def test_real_batch_second_line_failure_rolls_back_without_partial_commit_or_ack(self):
        service = InteractionService(MagicMock(), MagicMock(), MagicMock())
        self.consumer.interaction_service = service
        with patch.object(service, "_process_one", side_effect=[SimpleNamespace(id=RETURN_LINE),
                                                               RuntimeError("second line failed")]) as process:
            with self.assertRaisesRegex(RuntimeError, "second line failed"):
                self.consumer.consume_message(message(two_lines()))
        self.assertEqual(process.call_count, 2)
        self.db.commit.assert_not_called()
        self.db.rollback.assert_called()
        self.db.close.assert_called_once()
        self.kafka.commit.assert_not_called()

    def test_batch_or_db_failure_closes_and_rolls_back_without_ack(self):
        self.service.process_batch.side_effect = RuntimeError("DB failure")
        with self.assertRaisesRegex(RuntimeError, "DB failure"):
            self.consumer.consume_message(message(two_lines()))
        self.db.rollback.assert_called_once()
        self.db.close.assert_called_once()
        self.kafka.commit.assert_not_called()

    def test_offset_failure_is_propagated_after_processing_without_skip(self):
        self.kafka.commit.side_effect = RuntimeError("offset unavailable")
        with self.assertRaisesRegex(RuntimeError, "offset unavailable"):
            self.consumer.consume_message(message(payload()))
        self.service.process_batch.assert_called_once()
        self.db.close.assert_called_once()

    def test_worker_pauses_failed_partition_and_only_commits_other_partition(self):
        invalid = payload()
        invalid["items"][0]["quantity"] = "NaN"
        failed = message(invalid)
        other = message(payload(), partition=1)
        self.kafka.commit.return_value = committed(partition=1)
        settings = SimpleNamespace(kafka_order_completed_topic="order.completed",
                                   kafka_return_completed_topic=TOPIC,
                                   kafka_bootstrap_servers="unused", kafka_group_id="unused")
        worker = KafkaWorker(consumer=self.kafka, return_consumer=self.consumer,
                             purchase_consumer=MagicMock(), settings=settings, error_backoff=0)
        queue = iter([failed, other])

        def poll(**kwargs):
            try:
                return next(queue)
            except StopIteration:
                worker.running = False
                return None

        self.kafka.poll.side_effect = poll
        with patch.object(worker, "setup_signals"):
            worker.run()
        self.kafka.pause.assert_called_once()
        paused, = self.kafka.pause.call_args.args[0]
        self.assertEqual((paused.topic, paused.partition), (TOPIC, 0))
        self.kafka.commit.assert_called_once_with(message=other, asynchronous=False)
        self.kafka.close.assert_called_once()


class PurchaseBoundaryRegressionTests(unittest.TestCase):
    def setUp(self):
        self.source = json.loads(PURCHASE_FIXTURE.read_text(encoding="utf-8"))
        self.db = MagicMock()
        self.factory = MagicMock(return_value=self.db)
        self.service = MagicMock()
        self.service.process_batch.return_value = [SimpleNamespace(id=ORDER_LINE)]
        self.kafka = MagicMock()
        self.kafka.commit.return_value = committed(topic="order.completed")
        self.consumer = PurchaseKafkaConsumer(consumer=self.kafka, interaction_service=self.service,
                                             session_factory=self.factory, settings=SimpleNamespace())

    def test_purchase_fixture_still_uses_whole_order_batch_and_business_time(self):
        msg = message(self.source, topic="order.completed")
        self.consumer.consume_message(msg)
        self.service.process_batch.assert_called_once()
        rows = self.service.process_batch.call_args.args[1]
        self.assertEqual(rows[0].event_type, "PURCHASE")
        self.assertEqual(rows[0].event_time.isoformat(), self.source["completedAt"].replace("Z", "+00:00"))
        self.assertEqual(self.service.process_batch.call_args.kwargs, {"completed_order": self.source})
        self.kafka.commit.assert_called_once_with(message=msg, asynchronous=False)

    def test_purchase_invalid_status_or_header_still_cannot_write_or_ack(self):
        for value, supplied in ((dict(self.source, orderStatus="PAYMENT_CONFIRMED"), None),
                                (self.source, [("event_version", b"2")])):
            with self.subTest(headers=supplied), self.assertRaises(PurchaseEventValidationError):
                self.consumer.consume_message(message(value, supplied, topic="order.completed"))
        self.factory.assert_not_called()
        self.service.process_batch.assert_not_called()
        self.kafka.commit.assert_not_called()


if __name__ == "__main__":
    unittest.main()
