"""Raw bytes, approved raw replay and pure hashes; no broker/DB or real keys."""
from copy import deepcopy
from datetime import datetime, timezone
from decimal import Decimal, localcontext
import json
from pathlib import Path
from types import SimpleNamespace
import traceback
import unittest
from unittest.mock import MagicMock

from app.kafka.payload_decoder import (
    KafkaPayloadDecodeError, KafkaPayloadShapeError, decode_kafka_payload,
)
from app.kafka.purchase_consumer import PurchaseKafkaConsumer
from app.kafka.purchase_event_adapter import PurchaseEventAdapter, PurchaseEventValidationError
from app.kafka.return_consumer import ReturnKafkaConsumer
from app.kafka.return_event_adapter import ReturnEventAdapter, ReturnEventValidationError
from app.kafka.receipt_canonical import (
    MissingHashKeyError, ReceiptPolicyError, canonical_bytes, keyed_digest,
)
from app.kafka.receipt_projection import project_receipt, replay_projection, ApprovedReceipt
from app.kafka.receipt_hashes import (
    approved_payload_hash, business_claims, event_integrity, verified_replay, wire_fingerprint,
    event_identity,
)
from tests.test_return_validation import payload, two_lines, headers, message, committed

FIXTURE = (Path(__file__).resolve().parents[2]
           / "Order-Service/src/test/resources/contracts/product-completed-v1.json")
# Non-production fixed keys, used only in this test module.
KEYS = {"test-old": b"receipt-test-old-key-0000000000000",
        "test-new": b"receipt-test-new-key-0000000000000"}
KEY_VERSION = "test-old"
PRIVATE = "private-person@example.test"


def raw(value):
    return json.dumps(value, ensure_ascii=True, separators=(",", ":")).encode()


def number_wire(token, *, purchase=False, field="quantity"):
    source = json.loads(FIXTURE.read_text(encoding="utf-8")) if purchase else payload()
    source["items"][0][field] = "__RAW_NUMBER__"
    return raw(source).replace(b'"__RAW_NUMBER__"', token.encode())


def representations(wire):
    return [wire, b'{"schema":null,"payload":' + wire + b'}',
            raw({"schema": {"type": "string"}, "payload": wire.decode()})]


def projection(wire, supplied_headers=None):
    return project_receipt(wire, supplied_headers, keys=KEYS, key_version=KEY_VERSION)


def event_hash(wire, supplied_headers=None, *, keys=KEYS, key_version=KEY_VERSION):
    return event_integrity(wire, headers=supplied_headers, keys=keys, key_version=key_version)


class StrictWireDecoderTests(unittest.TestCase):
    def test_exact_decimal_lexemes_and_integer_contract_type(self):
        decoded = decode_kafka_payload(b'{"quantity":1.0000,"precise":1.0000000000000001,"version":1,"zero":-0}')
        self.assertEqual(str(decoded["quantity"]), "1.0000")
        self.assertEqual(str(decoded["precise"]), "1.0000000000000001")
        self.assertIs(type(decoded["version"]), int)
        self.assertEqual(decoded.numeric_tokens["/zero"], {"kind": "integer", "token": "-0"})
        self.assertEqual(decoded.numeric_tokens["/quantity"]["token"], "1.0000")
        self.assertNotIn("numeric_tokens", decoded)

    def test_raw_return_scale_and_precision_bypass_now_reject(self):
        for token in ("1.0000", "1.0000000000000001", "0.0001", "1e-4", "1.0000e0", "100000000000"):
            for wire in representations(number_wire(token)):
                with self.subTest(token=token, wrapper=wire[:16]), self.assertRaises(ReturnEventValidationError):
                    ReturnEventAdapter().to_canonical_interactions(decode_kafka_payload(wire))

    def test_raw_return_exact_boundaries_and_exponents(self):
        for token, expected in (("0.001", "0.001"), ("99999999999.999", "99999999999.999"),
                                ("1e10", "1E+10"), ("25e-1", "2.5"), ("1.000", "1.000")):
            with self.subTest(token=token):
                line, = ReturnEventAdapter().to_canonical_interactions(decode_kafka_payload(number_wire(token)))
                self.assertEqual(line.quantity.as_tuple(), Decimal(expected).as_tuple())

    def test_purchase_keeps_value_scale_policy_and_exact_price(self):
        # PURCHASE historically accepts redundant trailing zeros; RETURN supplied-scale is stricter.
        line, = PurchaseEventAdapter().to_canonical_interactions(decode_kafka_payload(number_wire("1.0000", purchase=True)))
        self.assertEqual(line.quantity, Decimal("1"))
        for token in ("1.0000000000000001", "99999999999.9991", "1e-4"):
            with self.subTest(token=token), self.assertRaises(PurchaseEventValidationError):
                PurchaseEventAdapter().to_canonical_interactions(decode_kafka_payload(number_wire(token, purchase=True)))
        line, = PurchaseEventAdapter().to_canonical_interactions(decode_kafka_payload(number_wire("999999999999.99", purchase=True, field="unitPrice")))
        self.assertEqual(line.unit_price, Decimal("999999999999.99"))
        with self.assertRaises(PurchaseEventValidationError):
            PurchaseEventAdapter().to_canonical_interactions(decode_kafka_payload(number_wire("1.0000000000000001", purchase=True, field="unitPrice")))

    def test_duplicate_keys_reject_every_object_and_representation(self):
        malformed = [b'{"eventId":"a","eventId":"b"}',
                     b'{"items":[{"quantity":1,"quantity":2}]}',
                     b'{"schema":{"type":"a","type":"b"},"payload":{}}',
                     b'{"schema":null,"payload":{},"payload":{}}',
                     b'{"schema":null,"payload":{"eventId":"a","eventId":"b"}}',
                     raw({"schema": None, "payload": '{"items":[{"quantity":1,"quantity":2}]}'}),
                     b'{"eventId":"a","\\u0065ventId":"b"}']
        for wire in malformed:
            with self.subTest(wire=wire), self.assertRaises(KafkaPayloadDecodeError):
                decode_kafka_payload(wire)

    def test_nonstandard_or_invalid_json_numbers_reject_before_adapter(self):
        for token in ("NaN", "Infinity", "-Infinity", "+1", "01", "1.", ".1", "1e", "1e9999999999999999999999999"):
            with self.subTest(token=token), self.assertRaises(KafkaPayloadDecodeError):
                decode_kafka_payload(number_wire(token))

    def test_json_types_missing_null_and_numeric_strings_are_distinct(self):
        value = decode_kafka_payload(b'{"n":null,"b":true,"s":"1.0000","d":1.0000,"i":1,"a":[],"o":{}}')
        self.assertNotIn("missing", value)
        self.assertIsNone(value["n"])
        self.assertIs(value["b"], True)
        self.assertIs(type(value["s"]), str)
        self.assertIsInstance(value["d"], Decimal)
        self.assertIs(type(value["i"]), int)
        self.assertEqual((value["a"], value["o"]), ([], {}))

    def test_plain_envelope_and_nested_valid_purchase_return_compatibility(self):
        for source, adapter in ((payload(), ReturnEventAdapter()),
                                (json.loads(FIXTURE.read_text(encoding="utf-8")), PurchaseEventAdapter())):
            original = adapter.to_canonical_interactions(source)
            for wire in representations(raw(source)):
                with self.subTest(event=source["eventType"], wrapper=wire[:16]):
                    actual = adapter.to_canonical_interactions(decode_kafka_payload(wire))
                    self.assertEqual([x.model_dump() for x in actual], [x.model_dump() for x in original])

    def test_fractional_numeric_epoch_seconds_and_milliseconds(self):
        expected = datetime(2026, 11, 6, 1, 30, 0, 125000, tzinfo=timezone.utc)
        for purchase in (False, True):
            for token in ("1793928600.125", "1793928600125.0"):
                source = json.loads(FIXTURE.read_text(encoding="utf-8")) if purchase else payload()
                source.pop("completedAt", None)
                source["occurredAt"] = "__TIME__"
                value = decode_kafka_payload(raw(source).replace(b'"__TIME__"', token.encode()))
                adapter = PurchaseEventAdapter() if purchase else ReturnEventAdapter()
                with self.subTest(purchase=purchase, token=token):
                    self.assertEqual(adapter.to_canonical_interactions(value)[0].event_time, expected)

    def test_epoch_invalid_boolean_nonfinite_overflow(self):
        for adapter in (PurchaseEventAdapter(), ReturnEventAdapter()):
            for value in (True, Decimal("NaN"), Decimal("Infinity"), Decimal("1e50")):
                with self.subTest(adapter=type(adapter).__name__, value=str(value)), self.assertRaises(Exception):
                    adapter._parse_occurred_at(value)

    def test_shape_and_encoding_errors_are_controlled_without_values(self):
        for index, wire in enumerate((None, b'\xff', b'[]', b'null', b'{"schema":null}', b'{"schema":null,"payload":null}',
                     raw({"schema": None, "payload": "[]"}), raw(PRIVATE), b'{"' + PRIVATE.encode() + b'":')):
            with self.subTest(case=index):
                try:
                    decode_kafka_payload(wire)
                except KafkaPayloadDecodeError:
                    self.assertNotIn(PRIVATE, traceback.format_exc())
                else:
                    self.fail("Malformed input unexpectedly accepted")

    def test_dict_compatibility_does_not_claim_lost_float_tokens(self):
        value = decode_kafka_payload({"quantity": 1.25})
        self.assertEqual(value.numeric_tokens, {})
        with self.assertRaises(ReceiptPolicyError):
            canonical_bytes(value)
        self.assertIs(decode_kafka_payload(value), value)

    def test_decimal_timestamp_bridge_is_independent_of_decimal_context(self):
        from app.kafka.numeric_time import decimal_epoch
        with localcontext() as context:
            context.prec = 6
            self.assertEqual(decimal_epoch(Decimal("1793928600.125")), datetime(2026, 11, 6, 1, 30, 0, 125000, tzinfo=timezone.utc))


class ApprovedProjectionTests(unittest.TestCase):
    def test_known_pii_and_free_text_are_dropped_without_mutating_source(self):
        source = payload()
        source.update(customerName=PRIVATE)  # Unknown on RETURN, never copied.
        source["items"][0]["note"] = PRIVATE
        receipt = projection(raw(source))
        self.assertNotIn(PRIVATE, json.dumps(receipt.document))
        self.assertEqual(receipt.replay_level, "DIAGNOSTIC_ONLY")
        source.pop("customerName")
        receipt = projection(raw(source))
        self.assertEqual(receipt.replay_level, "POLICY_PROJECTION")
        replay = replay_projection(receipt)
        self.assertNotIn("note", replay["items"][0])
        self.assertEqual(source["items"][0]["note"], PRIVATE)

    def test_purchase_allowlist_preserves_all_validator_dependencies(self):
        source = json.loads(FIXTURE.read_text(encoding="utf-8"))
        source.update(customerName=PRIVATE, customerEmail=PRIVATE, customerPhone=PRIVATE,
                      cashierName=PRIVATE, branchName=PRIVATE)
        source["items"][0].update(productName=PRIVATE, variantName=PRIVATE)
        receipt = projection(raw(source))
        self.assertEqual(receipt.replay_level, "POLICY_PROJECTION")
        self.assertNotIn(PRIVATE, json.dumps(receipt.document))
        replay = replay_projection(receipt)
        old = PurchaseEventAdapter().to_canonical_interactions(source)[0]
        new = PurchaseEventAdapter().to_canonical_interactions(replay)[0]
        self.assertEqual(old.model_dump(exclude={"metadata"}), new.model_dump(exclude={"metadata"}))

    def test_numeric_raw_tokens_and_types_survive_json_storage_round_trip(self):
        receipt = projection(number_wire("25.000e-1"))
        restored = ApprovedReceipt(json.loads(json.dumps(receipt.document)))
        replay = verified_replay(restored, approved_payload_hash(receipt).value)
        self.assertEqual(replay.numeric_tokens["/items/0/quantity"]["token"], "25.000e-1")
        with self.assertRaises(ReturnEventValidationError):
            ReturnEventAdapter().to_canonical_interactions(replay)  # scale4 remains invalid.

    def test_missing_null_and_boolean_are_preserved_without_repair(self):
        source = payload(); source.pop("customerId")
        source["items"][0]["quantity"] = True
        replay = replay_projection(projection(raw(source)))
        self.assertNotIn("customerId", replay)
        self.assertIs(replay["items"][0]["quantity"], True)
        with self.assertRaises(ReturnEventValidationError):
            ReturnEventAdapter().to_canonical_interactions(replay)

    def test_operational_lines_and_order_survive_before_reason_filter(self):
        source = two_lines(); source["items"][0]["returnReason"] = "DEFECTIVE"
        replay = replay_projection(projection(raw(source), headers(source)))
        self.assertEqual([x["returnReason"] for x in replay["items"]], ["DEFECTIVE", "CHANGE_OF_MIND"])
        self.assertEqual(len(ReturnEventAdapter().to_canonical_interactions(replay)), 1)

    def test_compatibility_product_id_and_item_type_are_not_redacted(self):
        source = json.loads(FIXTURE.read_text(encoding="utf-8"))
        source["items"][0].update(productId="550e8400-e29b-41d4-a716-446655440004", itemType="CUSTOM_BUNDLE")
        replay = replay_projection(projection(raw(source)))
        self.assertIn("productId", replay["items"][0])
        with self.assertRaises(PurchaseEventValidationError):
            PurchaseEventAdapter().to_canonical_interactions(replay)

    def test_arbitrary_unknown_fields_and_names_are_digest_only(self):
        for path in ("root", "line"):
            source = payload(); target = source if path == "root" else source["items"][0]
            target[PRIVATE] = {"secret": PRIVATE}
            with self.subTest(path=path):
                receipt = projection(raw(source))
                self.assertEqual(receipt.replay_level, "DIAGNOSTIC_ONLY")
                self.assertNotIn(PRIVATE, json.dumps(receipt.document))
                self.assertEqual(receipt.document["diagnostics"][0]["code"], "UNCLASSIFIED_FIELD")

    def test_invalid_nested_or_free_text_values_are_not_copied_or_repaired(self):
        for field in ("customerId", "quantity", "returnReason", "condition", "sku", "occurredAt"):
            for bad in (PRIVATE, {PRIVATE: [PRIVATE]}):
                source = payload(); target = source if field in ("customerId", "occurredAt") else source["items"][0]
                target[field] = bad
                with self.subTest(field=field, value_type=type(bad).__name__):
                    receipt = projection(raw(source))
                    self.assertEqual(receipt.replay_level, "DIAGNOSTIC_ONLY")
                    self.assertNotIn(PRIVATE, json.dumps(receipt.document))
                    with self.assertRaises(ReceiptPolicyError):
                        replay_projection(receipt)

    def test_unknown_schema_version_bad_json_and_utf8_have_controlled_diagnostics(self):
        for index, wire in enumerate((raw(dict(payload(), eventType=PRIVATE)), raw(dict(payload(), eventVersion=2)),
                     b'\xff', b'{"' + PRIVATE.encode() + b'":', b'[]')):
            with self.subTest(case=index):
                receipt = projection(wire)
                self.assertEqual(receipt.replay_level, "DIAGNOSTIC_ONLY")
                self.assertTrue(receipt.document["diagnostics"])
                self.assertNotIn(PRIVATE, json.dumps(receipt.document))

    def test_header_order_duplicate_null_and_presence_survive(self):
        supplied = [("event_version", b"1"), ("event_version", b"1"), ("saga_id", None)]
        receipt = projection(raw(payload()), supplied)
        self.assertEqual(receipt.document["headers"], [["event_version", "1"], ["event_version", "1"], ["saga_id", None]])
        self.assertTrue(receipt.document["headers_present"])
        self.assertFalse(projection(raw(payload())).document["headers_present"])
        with self.assertRaises(ReturnEventValidationError):
            ReturnEventAdapter.validate_headers(replay_projection(receipt), receipt.document["headers"])

    def test_invalid_or_unrelated_headers_never_copy_private_values(self):
        for supplied in ([('traceparent', PRIVATE.encode())], [('event_id', PRIVATE.encode())],
                         [('event_id', b'\xff')], [('event_version', 1)], [None], {PRIVATE: PRIVATE}):
            with self.subTest(header_kind=type(supplied).__name__):
                receipt = projection(raw(payload()), supplied)
                self.assertNotIn(PRIVATE, json.dumps(receipt.document))
                unrelated = isinstance(supplied, list) and supplied[0] and supplied[0][0] == "traceparent"
                if unrelated:
                    self.assertEqual(receipt.replay_level, "POLICY_PROJECTION")
                else:
                    self.assertEqual(receipt.replay_level, "DIAGNOSTIC_ONLY")

    def test_tampered_replay_document_is_rejected_by_hash(self):
        receipt = projection(raw(payload())); digest = approved_payload_hash(receipt).value
        corrupt = deepcopy(receipt.document); corrupt["headers_present"] = True
        with self.assertRaisesRegex(ReceiptPolicyError, "HASH_MISMATCH"):
            verified_replay(ApprovedReceipt(corrupt), digest)

    def test_policy_and_key_dependencies_fail_closed(self):
        with self.assertRaises(MissingHashKeyError):
            project_receipt(raw(payload()), keys={}, key_version=KEY_VERSION)
        with self.assertRaises(ReceiptPolicyError):
            project_receipt(raw(payload()), keys=KEYS, key_version=KEY_VERSION, policy_version="unknown")
        with self.assertRaises(ReceiptPolicyError):
            project_receipt(payload(), keys=KEYS, key_version=KEY_VERSION)


class ReceiptHashTests(unittest.TestCase):
    def test_event_identity_key_excludes_type_and_keeps_legacy_inference_explicit(self):
        source = payload(); source["eventId"] = "  " + source["eventId"].upper() + "  "
        first = event_identity(raw(source), namespace="test")
        self.assertTrue(first.source_inferred and first.version_inferred)
        source.update(eventType="ORDER_COMPLETED", eventVersion=1, sourceService="order-service")
        second = event_identity(raw(source), namespace="test")
        self.assertEqual(first.key, second.key)
        self.assertNotEqual(first.event_type, second.event_type)
        self.assertFalse(second.version_inferred)
        self.assertNotIn("sourceService", replay_projection(projection(raw(payload()))))
        with self.assertRaises(ReceiptPolicyError):
            event_identity(raw(dict(source, eventVersion=True)), namespace="test")

    def test_frozen_vectors_for_each_hash_role(self):
        wire = raw(payload())
        self.assertEqual(wire_fingerprint(wire, keys=KEYS, key_version=KEY_VERSION).value,
                         "384b13d73052565c6d1735d0bbfd237fa8ea58f4da7001a13c5b3e8f56a86641")
        self.assertEqual(event_hash(wire).value,
                         "298c98c19eecf969c18082756f66b8bb92241169d2605969755297aa058000bd")
        self.assertEqual(approved_payload_hash(projection(wire)).value,
                         "3ab8c9342b6b58bc9fa84b9a5620762330576ec88975e48d52c6f621b92619a8")
        self.assertEqual([c.content_hash for c in business_claims(wire, namespace="test")], [
            "40c3db75c56e8679c2d7b0be084c27155abbc7888878bdb25f378925e2f5c814",
            "2a156daff05cfc58e9069a11374cbd6bc5d101da4b76c8f35a5ab37863cf8256"])

    def test_event_hash_is_stable_across_object_order_whitespace_and_wrappers(self):
        source = payload(); expected = event_hash(raw(source))
        variants = representations(raw(source)) + [json.dumps(dict(reversed(list(source.items()))), indent=2).encode()]
        for wire in variants:
            with self.subTest(wrapper=wire[:16]):
                self.assertEqual(event_hash(wire), expected)

    def test_wire_and_event_integrity_and_approved_hash_have_different_roles(self):
        source = payload(); source["items"][0]["note"] = PRIVATE
        first = raw(source); source["items"][0]["note"] = "changed private note"; second = raw(source)
        self.assertNotEqual(event_hash(first), event_hash(second))
        self.assertEqual(approved_payload_hash(projection(first)), approved_payload_hash(projection(second)))
        wrapped = representations(first)[1]
        self.assertEqual(event_hash(first), event_hash(wrapped))
        self.assertNotEqual(wire_fingerprint(first, keys=KEYS, key_version=KEY_VERSION),
                            wire_fingerprint(wrapped, keys=KEYS, key_version=KEY_VERSION))

    def test_event_integrity_distinguishes_numeric_tokens_scale_and_type(self):
        values = [event_hash(number_wire(x)).value for x in ("1", "1.0", "1.00", "1e0", "-0")]
        values.append(event_hash(raw(dict(payload(), items=[dict(payload()["items"][0], quantity="1")]))).value)
        self.assertEqual(len(set(values)), len(values))

    def test_wire_key_header_order_duplicate_null_empty_and_bytes_are_framed(self):
        def digest(**kwargs):
            return wire_fingerprint(b"{}", keys=KEYS, key_version=KEY_VERSION, **kwargs).value
        values = [digest(), digest(record_key=b""), digest(record_key=b"key"), digest(headers=[]),
                  digest(headers=[("a", None)]), digest(headers=[("a", b"")]),
                  digest(headers=[("a", b"x"), ("b", b"y")]),
                  digest(headers=[("b", b"y"), ("a", b"x")]),
                  digest(headers=[("a", b"x"), ("a", b"x")])]
        self.assertEqual(len(set(values)), len(values))

    def test_event_ignores_unrelated_headers_and_validates_header_shape(self):
        self.assertEqual(event_hash(raw(payload()), []), event_hash(raw(payload()), [("traceparent", b"unrelated")]))
        self.assertNotEqual(event_hash(raw(payload()), []), event_hash(raw(payload()), [("event_version", b"1")]))
        for supplied in ([None], [("event_id", b'\xff')], [("event_id", 1)], "headers"):
            with self.subTest(shape=type(supplied).__name__), self.assertRaises(ReceiptPolicyError):
                event_hash(raw(payload()), supplied)

    def test_rotation_requires_original_key_version_and_never_prints_key(self):
        wire = raw(payload()); old = event_hash(wire); new = event_hash(wire, key_version="test-new")
        self.assertNotEqual(old.value, new.value)
        self.assertEqual(old, event_hash(wire, keys=KEYS, key_version=old.key_version))
        with self.assertRaises(MissingHashKeyError) as caught:
            event_hash(wire, keys={"test-new": KEYS["test-new"]}, key_version=old.key_version)
        self.assertEqual(str(caught.exception), "HASH_KEY_UNAVAILABLE")
        self.assertNotIn(KEYS[KEY_VERSION].decode(), repr(old))

    def test_policy_domain_separation_and_unsupported_versions(self):
        self.assertNotEqual(canonical_bytes({"x": 1}, policy_version="v1"), canonical_bytes({"x": 1}, policy_version="v2"))
        for function in (event_integrity, wire_fingerprint):
            with self.subTest(helper=function.__name__), self.assertRaises(ReceiptPolicyError):
                function(b"{}", keys=KEYS, key_version=KEY_VERSION, policy_version="v2")
        with self.assertRaises(ReceiptPolicyError):
            approved_payload_hash(projection(raw(payload())), policy_version="v2")
        with self.assertRaises(ReceiptPolicyError):
            business_claims(raw(payload()), namespace="test", policy_version="v2")

    def test_event_id_is_independent_of_business_identity_and_numeric_value_equality(self):
        first = number_wire("1.000")
        source = payload(); source["eventId"] = "550e8400-e29b-41d4-a716-446655440099"
        source["items"][0]["quantity"] = 1
        second = raw(source)
        self.assertNotEqual(event_hash(first), event_hash(second))
        self.assertEqual(business_claims(first, namespace="test"), business_claims(second, namespace="test"))

    def test_same_business_id_changed_content_produces_conflict_candidate(self):
        first = business_claims(raw(payload()), namespace="test")
        for field, value in (("quantity", "3.000"), ("returnReason", "DEFECTIVE"), ("condition", "OPENED")):
            source = payload(); source["items"][0][field] = value
            with self.subTest(field=field):
                second = business_claims(raw(source), namespace="test")
                self.assertEqual([(c.kind, c.identity) for c in first], [(c.kind, c.identity) for c in second])
                self.assertNotEqual([c.content_hash for c in first], [c.content_hash for c in second])

    def test_completion_hash_is_order_independent_and_scope_sensitive(self):
        source = two_lines(); first = business_claims(raw(source), namespace="test")
        source["items"].reverse()
        self.assertEqual(first, business_claims(raw(source), namespace="test"))
        self.assertNotEqual(first, business_claims(raw(source), namespace="production"))
        source["items"].pop()
        old = next(c for c in first if c.kind == "RETURN_COMPLETION")
        new = next(c for c in business_claims(raw(source), namespace="test") if c.kind == "RETURN_COMPLETION")
        self.assertNotEqual(old.content_hash, new.content_hash)

    def test_business_time_is_semantic_but_purchase_emission_is_not(self):
        source = json.loads(FIXTURE.read_text(encoding="utf-8")); first = business_claims(raw(source), namespace="test")
        source.update(eventId="550e8400-e29b-41d4-a716-446655440099", occurredAt="2026-10-01T09:00:00Z")
        self.assertEqual(first, business_claims(raw(source), namespace="test"))
        source["completedAt"] = "2026-10-01T07:00:00Z"
        self.assertNotEqual(first, business_claims(raw(source), namespace="test"))
        source = payload(); initial = business_claims(raw(source), namespace="test")
        source["occurredAt"] = "2026-11-06T08:31:00+07:00"
        self.assertNotEqual(initial, business_claims(raw(source), namespace="test"))

    def test_invalid_business_inputs_never_return_a_claim(self):
        values = [dict(payload(), eventId=PRIVATE), dict(payload(), eventVersion=2),
                  dict(payload(), items=[]), dict(payload(), newStatus="CREATED")]
        for index, value in enumerate(values):
            with self.subTest(case=index), self.assertRaises(ReceiptPolicyError) as caught:
                business_claims(raw(value), namespace="test")
            self.assertNotIn(PRIVATE, str(caught.exception))

    def test_business_equality_is_decimal_context_independent(self):
        wire = number_wire("99999999999.999")
        original = business_claims(wire, namespace="test")
        with localcontext() as context:
            context.prec = 20
            self.assertEqual(original, business_claims(wire, namespace="test"))

    def test_fixed_canonical_and_hmac_vector(self):
        # Independently specified bytes for the typed encoding and framed HMAC.
        expected = b'["hvtra-json-lexical-v1",["object",[["a",["integer","1"]],["b",["string","1"]]]]]'
        self.assertEqual(canonical_bytes(decode_kafka_payload(b'{"b":"1","a":1}')), expected)
        self.assertEqual(keyed_digest("vector", ["x", 1], keys=KEYS, key_version=KEY_VERSION).value,
                         "2f041da660232fd82d7c9301ca89e06da527cbfe81e44596f1993ef8e869d4b6")


class StrictConsumerBoundaryTests(unittest.TestCase):
    def test_whole_order_evidence_accepts_exact_decimals_without_serializing_raw_payload(self):
        from app.service.report_purchase_service import record_completed_order
        for token in ("2.500", "99999999999.999", "1.0000"):
            wire = number_wire(token, purchase=True)
            source = decode_kafka_payload(wire)
            row, = PurchaseEventAdapter().to_canonical_interactions(source)
            event = SimpleNamespace(**row.model_dump())
            event.event_time = row.event_time.astimezone(timezone.utc).replace(tzinfo=None)
            event.product_id = "550e8400-e29b-41d4-a716-446655440777"
            db = MagicMock(); db.scalar.return_value = None
            with self.subTest(token=token):
                record_completed_order(db, source, [event])
                evidence = db.add.call_args.args[0]
                self.assertEqual(Decimal(evidence.lines[0]["quantity"]), Decimal(token))
                self.assertIs(type(evidence.lines[0]["quantity"]), str)
                db.commit.assert_not_called()  # Existing recorder remains transaction-neutral.

    def test_invalid_wire_never_opens_db_or_calls_business_or_acks(self):
        for purchase in (False, True):
            for token in ("1.0000000000000001", "NaN"):
                factory = MagicMock(); service = MagicMock(); kafka = MagicMock()
                cls = PurchaseKafkaConsumer if purchase else ReturnKafkaConsumer
                consumer = cls(consumer=kafka, interaction_service=service, session_factory=factory,
                               settings=MagicMock())
                msg = message(payload()); msg.value.return_value = number_wire(token, purchase=purchase)
                with self.subTest(purchase=purchase, token=token), self.assertRaises(Exception):
                    consumer.consume_message(msg)
                factory.assert_not_called(); service.process_batch.assert_not_called(); kafka.commit.assert_not_called()

    def test_duplicate_event_or_line_key_never_opens_db_or_acks(self):
        for cls in (PurchaseKafkaConsumer, ReturnKafkaConsumer):
            for wire in (b'{"eventId":"a","eventId":"b"}', b'{"items":[{"quantity":1,"quantity":2}]}'):
                factory = MagicMock(); service = MagicMock(); kafka = MagicMock()
                consumer = cls(consumer=kafka, interaction_service=service, session_factory=factory, settings=MagicMock())
                msg = message(payload()); msg.value.return_value = wire
                with self.subTest(consumer=cls.__name__, wire=wire), self.assertRaises(KafkaPayloadDecodeError):
                    consumer.consume_message(msg)
                factory.assert_not_called(); service.process_batch.assert_not_called(); kafka.commit.assert_not_called()

    def test_valid_decimal_wire_preserves_batch_before_ack(self):
        for purchase in (False, True):
            factory = MagicMock(); service = MagicMock(); kafka = MagicMock()
            kafka.commit.return_value = committed(topic="order.completed" if purchase else "notification.return.completed")
            cls = PurchaseKafkaConsumer if purchase else ReturnKafkaConsumer
            consumer = cls(consumer=kafka, interaction_service=service, session_factory=factory, settings=MagicMock())
            msg = message(payload(), topic="order.completed" if purchase else "notification.return.completed")
            msg.value.return_value = number_wire("2.500", purchase=purchase)
            with self.subTest(purchase=purchase):
                consumer.consume_message(msg)
                self.assertEqual(service.process_batch.call_args.args[1][0].quantity, Decimal("2.500"))
                kafka.commit.assert_called_once_with(message=msg, asynchronous=False)

    def test_private_validation_errors_do_not_echo_values_or_chains(self):
        cases = [(PurchaseEventAdapter(), dict(json.loads(FIXTURE.read_text(encoding="utf-8")), occurredAt=PRIVATE)),
                 (ReturnEventAdapter(), dict(payload(), items=[dict(payload()["items"][0], returnReason=PRIVATE)]))]
        for adapter, value in cases:
            with self.subTest(adapter=type(adapter).__name__):
                try:
                    adapter.to_canonical_interactions(value)
                except Exception:
                    self.assertNotIn(PRIVATE, traceback.format_exc())
                else:
                    self.fail("Invalid value accepted")
