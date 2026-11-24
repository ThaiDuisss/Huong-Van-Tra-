import base64
from datetime import datetime
from decimal import Decimal
import hmac
import json
from types import SimpleNamespace
from unittest import TestCase
from unittest.mock import MagicMock, patch
from uuid import uuid4

from confluent_kafka import KafkaError
from pydantic import SecretStr
from sqlalchemy import create_engine, event, select, text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import sessionmaker

from app.kafka.payload_decoder import decode_kafka_payload
from app.kafka.purchase_consumer import PurchaseKafkaConsumer
from app.kafka.receipt_projection import ApprovedReceipt, replay_projection
from app.kafka.return_consumer import ReturnKafkaConsumer
from app.kafka.signed_order_event import OrderIngressError, POLICY, _node, _text
from app.kafka.worker import KafkaWorker
from app.models.business_report import CompletedPurchaseOrder
from app.models.customer_interaction import CustomerInteraction
from app.models.interaction_event import InteractionEvent
from app.models.order_event_inbox import OrderEventDelivery, OrderEventInbox, OrderEventStream, OrderReturnLine
from app.repositories.customer_interaction_repository import CustomerInteractionRepository
from app.repositories.explore_history_repository import ExploreHistoryRepository
from app.repositories.interaction_event_repository import InteractionEventRepository
from app.service.business_report_workbook import purchased_products
from app.service.interaction_service import InteractionService

KEY = bytes(range(32))
KEY_ID = "test-order"
TOPIC = "order.completed"
RETURN_TOPIC = "notification.return.completed"


def guid():
    return str(uuid4())


def digest(data, topic, record_key, key_id=KEY_ID, key_version="1"):
    context = b"".join(_text(b"s", field) for field in (POLICY, topic, record_key, key_id, key_version))
    body = decode_kafka_payload(json.dumps(data))
    return hmac.digest(KEY, context + _node(body), "sha256").hex()


class Record:
    def __init__(self, payload, offset, *, partition=0, key_id=KEY_ID, key_version="1"):
        self.body = payload.copy()
        purchase = payload["eventType"] == "ORDER_COMPLETED"
        self.topic_name = TOPIC if purchase else RETURN_TOPIC
        self.record_key = payload["orderId"] if purchase else payload["returnId"]
        self.number = offset
        self.partition_id = partition
        self.body["kafkaAuth"] = dict(
            keyId=key_id, keyVersion=key_version, signature=digest(payload, self.topic_name, self.record_key, key_id, key_version)
        )
        self.fields = [("event_id", payload["eventId"]), ("event_type", payload["eventType"]),
                       ("event_version", "1"), ("source_service", "order-service"),
                       ("aggregate_type", "ORDER" if purchase else "ORDER_RETURN"),
                       ("aggregate_id", self.record_key),
                       ("business_key", self.record_key + (":order-completed" if purchase else ":return-completed"))]
        self.raw_override = None

    def value(self):
        return self.raw_override or json.dumps({"schema": {}, "payload": json.dumps(self.body)}).encode()

    def headers(self):
        return [(key, value.encode()) for key, value in self.fields]

    def error(self):
        return None

    def offset(self):
        return self.number

    def partition(self):
        return self.partition_id

    def topic(self):
        return self.topic_name

    def key(self):
        return self.record_key.encode()


class SignedOrderIngressBoundaryTests(TestCase):
    def setUp(self):
        self.session = MagicMock()
        self.db = self.session.return_value.__enter__.return_value
        self.db.get_bind.return_value.dialect.name = "mysql"
        self.db.get.return_value = None
        self.kafka = MagicMock()
        self.settings = SimpleNamespace(
            order_kafka_verification_keys=SecretStr(json.dumps({KEY_ID: {"1": base64.b64encode(KEY).decode()}})),
            kafka_order_completed_topic=TOPIC, kafka_return_completed_topic=RETURN_TOPIC,
            kafka_bootstrap_servers="unused", kafka_group_id="offline-test",
        )
        self.purchase = PurchaseKafkaConsumer(consumer=self.kafka, session_factory=self.session,
                                              settings=self.settings)
        order_id = guid()
        self.record = Record(dict(eventId=guid(), eventKey=order_id, orderId=order_id, customerId=None,
                                  eventType="ORDER_COMPLETED", sourceService="order-service", eventVersion=1), 0)

    def test_diagnostic_only_receipt_commits_without_ack_even_on_redelivery(self):
        body = {name: value for name, value in self.record.body.items() if name != "kafkaAuth"}
        message = Record(dict(body, unsupportedField="synthetic"), self.record.offset())
        transaction = self.db.begin.return_value

        with self.assertRaisesRegex(OrderIngressError, "RECEIPT_DIAGNOSTIC_ONLY"):
            self.purchase.consume_message(message)
        transaction.__exit__.assert_called_once_with(None, None, None)
        receipt, delivery = (call.args[0] for call in self.db.add.call_args_list)
        self.assertEqual(receipt.receipt["replay_level"], "DIAGNOSTIC_ONLY")
        self.assertEqual(receipt.error_code, "RECEIPT_DIAGNOSTIC_ONLY")
        self.assertEqual(delivery.event_id, receipt.event_id)

        self.db.add.reset_mock()
        self.db.get.side_effect = (receipt, delivery)
        with self.assertRaisesRegex(OrderIngressError, "RECEIPT_DIAGNOSTIC_ONLY"):
            self.purchase.consume_message(message)
        self.db.add.assert_not_called()
        self.kafka.commit.assert_not_called()

    def test_ack_requires_successful_database_commit(self):
        receipt = SimpleNamespace(receipt={"replay_level": "POLICY_PROJECTION"}, status="APPLIED", error_code=None)
        transaction = self.db.begin.return_value

        def check_commit_order(**_):
            transaction.__exit__.assert_called_once_with(None, None, None)
            return [SimpleNamespace(topic=TOPIC, partition=0, offset=1, error=None)]

        self.kafka.commit.side_effect = check_commit_order
        with patch.object(self.purchase.inbox, "stage", return_value=([], receipt)):
            transaction.__exit__.side_effect = RuntimeError("fixture commit failure")
            with self.assertRaisesRegex(RuntimeError, "fixture commit failure"):
                self.purchase.consume_message(self.record)
            self.kafka.commit.assert_not_called()
            transaction.reset_mock(side_effect=True)
            transaction.__exit__.return_value = False
            self.assertEqual(self.purchase.consume_message(self.record), [])
        self.kafka.commit.assert_called_once_with(message=self.record, asynchronous=False)

    def test_unavailable_offset_stops_mocked_poll_without_processing_later_record(self):
        worker = KafkaWorker(consumer=self.kafka, purchase_consumer=self.purchase,
                             return_consumer=MagicMock(), settings=self.settings)
        error_record = MagicMock()
        error_record.error.return_value = KafkaError(KafkaError._AUTO_OFFSET_RESET)
        self.kafka.poll.side_effect = (error_record, self.record)
        with patch.object(worker, "setup_signals"), self.assertLogs("app.kafka.worker", "ERROR"):
            with self.assertRaisesRegex(RuntimeError, "KAFKA_OFFSET_REVIEW_REQUIRED"):
                worker.run()
        self.kafka.poll.assert_called_once()
        self.kafka.commit.assert_not_called()
        self.kafka.close.assert_called_once()
        self.session.assert_not_called()
        self.assertFalse(worker.running)

    def test_canonical_numbers_match_java_normalization_before_bounds(self):
        for wire, canonical in (("1.00" + "0" * 36, "1"), ("-0E-40", "0"),
                                ("12345678901234567890123456789012.00", "12345678901234567890123456789012"),
                                ("1E+32", "1" + "0" * 32), ("1" + "0" * 32, "1" + "0" * 32)):
            with self.subTest(wire=wire):
                self.assertEqual(_node(Decimal(wire)), _text(b"d", canonical))
        for wire in ("1E+33", "1E-33", "123456789012345678901234567890123"):
            with self.subTest(wire=wire), self.assertRaisesRegex(OrderIngressError, "CANONICAL_NUMBER_OUT_OF_BOUNDS"):
                _node(Decimal(wire))


class SignedOrderInboxTests(TestCase):
    def setUp(self):
        engine = create_engine("sqlite://")
        self.addCleanup(engine.dispose)

        @event.listens_for(engine, "connect")
        def foreign_keys(connection, _):
            connection.execute("PRAGMA foreign_keys = ON")

        for model in (CompletedPurchaseOrder, OrderEventStream, OrderEventInbox, OrderEventDelivery, OrderReturnLine):
            model.__table__.create(engine)
        with engine.begin() as db:
            db.execute(text("""CREATE TABLE interaction_events (
                id TEXT PRIMARY KEY, source_event_id TEXT, order_item_id TEXT, return_item_id TEXT,
                customer_id TEXT, product_variant_id TEXT, product_id TEXT, event_type TEXT, quantity NUMERIC,
                unit_price NUMERIC, order_id TEXT, return_id TEXT, return_reason TEXT, event_time DATETIME,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(source_event_id, order_item_id, event_type))"""))
            db.execute(text("""CREATE TABLE customer_interactions (
                customer_id TEXT, product_id TEXT, total_purchase_count INTEGER, total_purchase_quantity NUMERIC,
                total_return_count INTEGER, total_return_quantity NUMERIC, last_interaction_time DATETIME,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY(customer_id, product_id))"""))
        self.session = sessionmaker(engine, autoflush=False, expire_on_commit=False)
        self.kafka = MagicMock()
        self.kafka.commit.side_effect = lambda message, asynchronous: [
            SimpleNamespace(topic=message.topic(), partition=message.partition(), offset=message.offset() + 1, error=None)
        ]
        self.product = guid()
        self.variant = guid()
        self.bundle_variant = guid()
        self.resolver = MagicMock()
        self.resolver.resolve_variant.side_effect = lambda db, product_variant_id, sku: (
            SimpleNamespace(id=self.variant, product_id=self.product, sku="TEA") if product_variant_id == self.variant else None
        )
        self.service = InteractionService(self.resolver, InteractionEventRepository(), CustomerInteractionRepository())
        self.settings = SimpleNamespace(
            order_kafka_verification_keys=SecretStr(json.dumps({KEY_ID: {"1": base64.b64encode(KEY).decode()}})),
            kafka_order_completed_topic=TOPIC, kafka_return_completed_topic=RETURN_TOPIC,
            kafka_bootstrap_servers="unused", kafka_group_id="offline-test",
        )
        self.purchase = self.consumer()
        self.returns = self.consumer(ReturnKafkaConsumer)
        self.customer = guid()
        self.order_id = guid()
        self.product_line = {"orderItemId": guid(), "purchaseType": "PRODUCT", "productVariantId": self.variant,
                             "quantity": 2, "unitPrice": 30500, "bomVersionId": guid()}
        self.bundle_line = {"orderItemId": guid(), "purchaseType": "CUSTOM_BUNDLE",
                            "productVariantId": self.bundle_variant, "quantity": 1, "sku": "CUSTOM-BUNDLE",
                            "bomVersionId": None}
        self.base = dict(eventVersion=1, sourceService="order-service", orderId=self.order_id, customerId=self.customer)

    def consumer(self, kind=PurchaseKafkaConsumer, session_factory=None):
        return kind(consumer=self.kafka, interaction_service=self.service, settings=self.settings,
                    session_factory=session_factory or self.session)

    def order_message(self, lines=None, offset=0, **changes):
        payload = dict(self.base, eventId=guid(), eventKey=self.order_id, eventType="ORDER_COMPLETED",
                       occurredAt="2026-11-10T09:00:00Z", completedAt="2026-11-10T08:00:00Z",
                       orderStatus="COMPLETED", paymentStatus="PAID", customerEmail="private@example.test",
                       items=lines if lines is not None else [self.product_line])
        payload.update(changes)
        return Record(payload, offset)

    def return_message(self, line, quantity, offset, reason="DEFECTIVE"):
        return_id = guid()
        return Record(dict(self.base, eventId=guid(), eventKey=return_id, eventType="RETURN_COMPLETED",
                           returnId=return_id, occurredAt="2026-11-11T09:00:00Z", newStatus="COMPLETED",
                           items=[dict(returnItemId=guid(), orderItemId=line["orderItemId"],
                                       productVariantId=line["productVariantId"], quantity=quantity,
                                       returnReason=reason)]), offset)

    def count(self, model):
        with self.session() as db:
            return len(db.scalars(select(model)).all())

    def check_status(self, message, status, code=None):
        with self.session() as db:
            row = db.get(OrderEventInbox, message.body["eventId"])
            self.assertEqual((row.status, row.error_code), (status, code))

    def history(self):
        with self.session() as db:
            return ExploreHistoryRepository().history(db, self.customer, datetime(2027, 1, 1))

    def test_product_only_and_bom_identity(self):
        message = self.order_message()
        rows = self.purchase.consume_message(message)
        self.assertEqual([(row.product_id, row.product_variant_id) for row in rows], [(self.product, self.variant)])
        with self.session() as db:
            receipt = db.get(CompletedPurchaseOrder, self.order_id)
            self.assertEqual(receipt.lines[0]["bom_version_id"], self.product_line["bomVersionId"])
            self.assertEqual(receipt.occurred_at, datetime(2026, 11, 10, 8))
            inbox = db.get(OrderEventInbox, message.body["eventId"])
            stored = replay_projection(ApprovedReceipt(inbox.receipt))
            self.assertNotIn("customerEmail", stored)
            self.assertNotIn("kafkaAuth", stored)
        self.assertEqual(self.history().valid_order_count, 1)

    def test_mixed_receipt_all_lines_and_product_order_counts_once(self):
        extra = dict(self.product_line, orderItemId=guid())
        self.purchase.consume_message(self.order_message([self.bundle_line, self.product_line, extra]))
        with self.session() as db:
            receipt = db.get(CompletedPurchaseOrder, self.order_id)
            self.assertEqual(len(receipt.lines), 3)
            self.assertEqual(purchased_products(receipt), {self.product: 4})
            bundle = next(line for line in receipt.lines if line["purchase_type"] == "CUSTOM_BUNDLE")
            self.assertEqual(bundle["product_variant_id"], self.bundle_variant)
            self.assertIsNone(bundle["product_id"])
            self.assertIsNone(bundle["bom_version_id"])
            self.assertEqual(bundle["bom_identity_status"], "UNRESOLVED")
        self.assertEqual(self.count(InteractionEvent), 2)
        self.assertEqual(self.history().valid_order_count, 1)

    def test_bundle_only_and_operational_bundle_return_never_create_product(self):
        message = self.order_message([self.bundle_line])
        self.assertEqual(self.purchase.consume_message(message), [])
        self.returns.consume_message(self.return_message(self.bundle_line, 1, 0))
        self.assertEqual(self.count(OrderEventInbox), 2)
        self.assertEqual(self.count(CompletedPurchaseOrder), 1)
        self.assertEqual(self.count(OrderReturnLine), 1)
        self.assertEqual(self.count(InteractionEvent), 0)
        self.assertEqual(self.history().valid_order_count, 0)
        self.resolver.resolve_variant.assert_not_called()

    def test_returns_before_purchase_pending_then_matched(self):
        returned = self.return_message(self.product_line, 1, 0)
        self.assertEqual(self.returns.consume_message(returned), [])
        self.check_status(returned, "PENDING_PURCHASE", "PURCHASE_NOT_OBSERVED")
        self.assertEqual(self.count(OrderReturnLine), 0)
        self.kafka.commit.assert_called_once()
        self.purchase.consume_message(self.order_message([self.product_line, self.bundle_line]))
        self.check_status(returned, "APPLIED")
        self.assertEqual(self.count(OrderReturnLine), 1)
        self.assertEqual(self.count(InteractionEvent), 1)
        self.assertEqual(self.history().valid_order_count, 1)

    def test_operational_partial_then_preference_full_and_bundle_return_stay_on_line(self):
        self.purchase.consume_message(self.order_message([self.product_line, self.bundle_line]))
        self.returns.consume_message(self.return_message(self.bundle_line, 1, 0, "CHANGE_OF_MIND"))
        self.returns.consume_message(self.return_message(self.product_line, 1, 1))
        self.assertEqual(self.count(InteractionEvent), 1)
        self.assertEqual(self.history().valid_order_count, 1)
        self.returns.consume_message(self.return_message(self.product_line, 1, 2, "CHANGE_OF_MIND"))
        self.assertEqual(self.count(InteractionEvent), 2)
        self.assertEqual(self.count(OrderReturnLine), 3)
        self.assertEqual(self.history().valid_order_count, 0)
        with self.session() as db:
            self.assertEqual(db.get(CustomerInteraction, (self.customer, self.product)).total_return_quantity, Decimal("1"))

    def test_mismatched_return_and_excess_quantity_durably_unresolved(self):
        self.purchase.consume_message(self.order_message())
        bad = self.return_message(dict(self.product_line, productVariantId=self.bundle_variant), 1, 0)
        self.returns.consume_message(bad)
        self.check_status(bad, "UNRESOLVED", "RETURN_LINE_IDENTITY_MISMATCH")
        excess = self.return_message(self.product_line, 3, 1)
        self.returns.consume_message(excess)
        self.check_status(excess, "UNRESOLVED", "RETURN_QUANTITY_EXCEEDS_PURCHASE")
        self.assertEqual(self.count(OrderReturnLine), 0)

    def test_same_return_aggregate_with_changed_items_does_not_apply_again(self):
        self.purchase.consume_message(self.order_message())
        original = self.return_message(self.product_line, 1, 0)
        self.returns.consume_message(original)
        altered = {field: value for field, value in original.body.items() if field != "kafkaAuth"}
        altered.update(eventId=guid(), items=[dict(altered["items"][0], returnItemId=guid())])
        conflict = Record(altered, 1)
        self.returns.consume_message(conflict)
        self.check_status(conflict, "UNRESOLVED", "RETURN_AGGREGATE_CONFLICT")
        self.assertEqual(self.count(OrderReturnLine), 1)
        self.assertEqual(self.history().valid_order_count, 1)

    def test_same_variant_on_two_purchase_lines_only_returns_the_matching_line(self):
        second = dict(self.product_line, orderItemId=guid())
        self.purchase.consume_message(self.order_message([self.product_line, second, self.bundle_line]))
        self.returns.consume_message(self.return_message(second, 2, 0))
        self.assertEqual(self.history().valid_order_count, 1)
        self.assertEqual(self.history().product_counts, {self.product: 1})
        self.returns.consume_message(self.return_message(self.product_line, 2, 1))
        self.assertEqual(self.history().valid_order_count, 0)

    def test_missing_completion_time_stays_unresolved_with_raw_evidence(self):
        message = self.order_message(completedAt=None)
        self.purchase.consume_message(message)
        self.check_status(message, "UNRESOLVED", "INVALID_PURCHASE_EVIDENCE")
        self.assertEqual(self.count(CompletedPurchaseOrder), 0)
        self.assertEqual(self.count(InteractionEvent), 0)
        self.kafka.commit.assert_called_once()

    def test_guest_and_unresolved_product_identity_have_complete_receipt(self):
        unknown = dict(self.product_line, productVariantId=guid())
        message = self.order_message([unknown, self.bundle_line], customerId=None)
        self.purchase.consume_message(message)
        self.check_status(message, "UNRESOLVED", "PRODUCT_IDENTITY_UNRESOLVED")
        with self.session() as db:
            receipt = db.get(CompletedPurchaseOrder, self.order_id)
            self.assertIsNone(receipt.customer_id)
            self.assertEqual(len(receipt.lines), 2)
            self.assertEqual(purchased_products(receipt), {})

    def test_missing_signature_legacy_and_invalid_inputs_remain_unacked(self):
        session_factory = MagicMock()
        consumer = self.consumer(session_factory=session_factory)
        for mutate, code in (
            (lambda msg: msg.body.pop("kafkaAuth"), "KAFKA_SIGNATURE_MISSING_OR_MALFORMED"),
            (lambda msg: (msg.body.pop("kafkaAuth"), msg.body.pop("eventVersion")), "LEGACY_EVENT_METADATA_MISSING"),
            (lambda msg: msg.body.pop("sourceService"), "EVENT_METADATA_MISSING"),
            (lambda msg: msg.body.update(eventVersion=2), "UNSUPPORTED_EVENT_VERSION"),
            (lambda msg: msg.body.update(orderCode="altered"), "INVALID_KAFKA_SIGNATURE"),
            (lambda msg: setattr(msg, "raw_override", b'{"x":1,"x":2}'), "MALFORMED_KAFKA_PAYLOAD"),
            (lambda msg: msg.fields.append(("event_id", msg.body["eventId"])), "DUPLICATE_KAFKA_HEADER"),
            (lambda msg: msg.fields.__setitem__(2, ("event_version", "2")), "KAFKA_HEADER_BODY_MISMATCH"),
            (lambda msg: msg.fields.pop(3), "KAFKA_HEADER_MISSING"),
            (lambda msg: setattr(msg, "record_key", guid()), "RECORD_KEY_MISMATCH"),
            (lambda msg: setattr(msg, "topic_name", RETURN_TOPIC), "UNEXPECTED_KAFKA_TOPIC"),
            (lambda msg: msg.body["kafkaAuth"].update(keyId="unknown"), "UNKNOWN_KEY_ID"),
            (lambda msg: msg.body["kafkaAuth"].update(keyVersion="2"), "UNKNOWN_KEY_VERSION"),
        ):
            with self.subTest(code=code):
                event = self.order_message()
                mutate(event)
                with self.assertRaisesRegex(OrderIngressError, code):
                    consumer.consume_message(event)
        session_factory.assert_not_called()
        self.kafka.commit.assert_not_called()
        self.assertEqual(self.count(OrderEventInbox), 0)
        self.assertEqual(self.count(InteractionEvent), 0)

    def test_db_failure_rolls_back_durable_rows_and_no_ack(self):
        message = self.order_message()
        with self.session.begin() as db:
            db.execute(text("DROP TABLE report_purchase_orders"))
        with self.assertRaises(SQLAlchemyError):
            self.purchase.consume_message(message)
        self.assertEqual(self.count(OrderEventInbox), 0)
        self.assertEqual(self.count(InteractionEvent), 0)
        self.kafka.commit.assert_not_called()

    def test_crash_during_second_line_rolls_back_first_line_and_raw_receipt(self):
        message = self.order_message([self.product_line, dict(self.product_line, orderItemId=guid())])
        stage_interactions = self.service.stage_interactions
        first_line = self.product_line["orderItemId"]

        def crash(db, interactions):
            if interactions[0].order_item_id != first_line:
                raise RuntimeError("fixture crash before database commit")
            return stage_interactions(db, interactions)

        with patch.object(self.service, "stage_interactions", side_effect=crash):
            with self.assertRaisesRegex(RuntimeError, "fixture crash"):
                self.purchase.consume_message(message)
        self.assertEqual(self.count(OrderEventInbox), 0)
        self.assertEqual(self.count(CompletedPurchaseOrder), 0)
        self.assertEqual(self.count(InteractionEvent), 0)
        self.assertEqual(self.count(CustomerInteraction), 0)
        self.kafka.commit.assert_not_called()

    def test_crash_after_database_commit_then_redelivery_is_idempotent(self):
        message = self.order_message()
        success = self.kafka.commit.side_effect

        def fail_offset(**kwargs):
            self.assertEqual(self.count(OrderEventInbox), 1)
            self.assertEqual(self.count(CompletedPurchaseOrder), 1)
            self.assertEqual(self.count(InteractionEvent), 1)
            raise RuntimeError("crash before ACK")

        self.kafka.commit.side_effect = fail_offset
        with self.assertRaisesRegex(RuntimeError, "crash before ACK"):
            self.purchase.consume_message(message)
        self.kafka.commit.side_effect = success
        self.assertEqual(self.consumer().consume_message(message), [])
        message.number += 1
        self.assertEqual(self.consumer().consume_message(message), [])
        self.assertEqual(self.count(OrderEventInbox), 1)
        self.assertEqual(self.count(OrderEventDelivery), 2)
        self.assertEqual(self.count(InteractionEvent), 1)
        with self.session() as db:
            self.assertEqual(db.get(CustomerInteraction, (self.customer, self.product)).total_purchase_count, 1)

    def test_conflicting_event_id_and_record_location_never_replace_first_evidence(self):
        initial = self.order_message()
        self.purchase.consume_message(initial)
        changed = self.order_message(offset=1, eventId=initial.body["eventId"],
                                     lines=[dict(self.product_line, quantity=9)])
        with self.assertRaisesRegex(OrderIngressError, "ORDER_EVENT_IDENTITY_CONFLICT"):
            self.purchase.consume_message(changed)
        with self.assertRaisesRegex(OrderIngressError, "ORDER_EVENT_IDENTITY_CONFLICT"):
            self.purchase.consume_message(self.order_message())
        self.kafka.commit.assert_called_once()
        self.assertEqual(self.count(OrderEventInbox), 1)

    def test_cross_language_round_trip_vector(self):
        self.assertEqual(
            digest({"z": [False, None, 1.23, -0.0], "a": "Tr\u00e0 \U0001f375"},
                   TOPIC, "550e8400-e29b-41d4-a716-446655440001"),
            "3cd1522b5cc5d4759f356af55905c0f5db271a7aebb4dba727f8fe9cb2ba224b",
        )

    def test_debezium_json_string_wrapping_and_decimal_reformatting_keep_signature(self):
        message = self.order_message()
        body = json.dumps(message.body, ensure_ascii=False).replace('"quantity": 2', '"quantity": 2.000')
        with patch.object(message, "value", return_value=json.dumps(body).encode()), patch.object(
            message, "key", return_value=json.dumps(message.record_key).encode()
        ):
            self.purchase.consume_message(message)
        self.check_status(message, "APPLIED")
        self.kafka.commit.assert_called_once()

    def test_worker_never_commits_past_untrusted_record_even_if_poll_delivers_later_offset(self):
        invalid = self.order_message()
        invalid.body.pop("kafkaAuth")
        same_partition = self.order_message(offset=1)
        other_partition = self.order_message(offset=2)
        other_partition.partition_id = 1
        worker = KafkaWorker(consumer=self.kafka, purchase_consumer=self.purchase, return_consumer=self.returns,
                             settings=self.settings, error_backoff=0)
        messages = iter([invalid, same_partition, other_partition])

        def poll(**_):
            message = next(messages, None)
            if message is None:
                worker.running = False
            return message

        self.kafka.poll.side_effect = poll
        with patch.object(worker, "setup_signals"), self.assertLogs("app.kafka.worker", "ERROR"):
            worker.run()
        self.kafka.pause.assert_called_once()
        self.kafka.commit.assert_called_once_with(message=other_partition, asynchronous=False)
        self.assertEqual(self.count(OrderEventInbox), 1)
