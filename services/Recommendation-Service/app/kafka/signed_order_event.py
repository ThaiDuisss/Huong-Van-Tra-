"""Verify signed Order V1 before opening a database transaction."""
from dataclasses import dataclass
from decimal import Decimal
import base64
import hashlib
import hmac
import json
import re
from uuid import UUID

from app.kafka.payload_decoder import KafkaPayloadDecodeError, decode_kafka_payload
from app.kafka.receipt_projection import CONTRACT_HEADERS

POLICY = "hvtra-order-kafka-v1"
TOKEN = re.compile(r"[A-Za-z0-9_.-]{1,64}")
MAX_BYTES = 1024 * 1024


class OrderIngressError(ValueError):
    """Controlled input diagnostic. Never includes raw payload or secrets."""


def _text(tag, value):
    data = value.encode("utf-8", errors="strict")
    return _count(tag, len(data)) + data


def _count(tag, count):
    return tag + str(count).encode("ascii") + b":"


def _node(value):
    if value is None:
        return b"n"
    if type(value) is bool:
        return b"t" if value else b"f"
    if isinstance(value, str):
        return _text(b"s", value)
    if isinstance(value, (int, Decimal)):
        number = Decimal(value)
        if not number.is_finite() or abs(number.as_tuple().exponent) > 32 or len(number.as_tuple().digits) > 32:
            raise OrderIngressError("CANONICAL_NUMBER_OUT_OF_BOUNDS")
        fixed = format(number, "f") if number else "0"
        return _text(b"d", fixed.rstrip("0").rstrip(".") if "." in fixed else fixed)
    if isinstance(value, list):
        return _count(b"a", len(value)) + b"".join(_node(child) for child in value)
    if isinstance(value, dict):
        keys = sorted(value, key=lambda key: key.encode("utf-8", errors="strict"))
        return _count(b"o", len(keys)) + b"".join(_text(b"s", key) + _node(value[key]) for key in keys)
    raise OrderIngressError("CANONICAL_TYPE_UNSUPPORTED")


def load_keyring(encoded: str) -> dict[tuple[str, str], bytes]:
    def only_unique(pairs):
        if len(dict(pairs)) != len(pairs):
            raise ValueError
        return dict(pairs)

    try:
        configured = json.loads(encoded, object_pairs_hook=only_unique)
        keys = {}
        for key_id, versions in configured.items():
            if not TOKEN.fullmatch(key_id) or not versions:
                raise ValueError
            for version, value in versions.items():
                secret = base64.b64decode(value, validate=True)
                if not TOKEN.fullmatch(version) or len(secret) < 32:
                    raise ValueError
                keys[key_id, version] = secret
        if not keys:
            raise ValueError
        return keys
    except (TypeError, ValueError, AttributeError):
        raise OrderIngressError("KAFKA_SIGNING_KEYS_UNCONFIGURED_OR_INVALID") from None


def require_uuid(value, code="INVALID_EVENT_IDENTITY"):
    try:
        if isinstance(value, str) and str(UUID(value)) == value:
            return value
    except ValueError:
        pass
    raise OrderIngressError(code)


def _record_key(value):
    if not isinstance(value, bytes) or len(value) > 256:
        raise OrderIngressError("RECORD_KEY_MISMATCH")
    try:
        return json.loads(value) if value.startswith(b'"') else value.decode("ascii")
    except (ValueError, UnicodeError):
        raise OrderIngressError("RECORD_KEY_MISMATCH") from None


@dataclass(frozen=True)
class SignedOrderEvent:
    payload: dict
    raw: bytes
    headers: list
    fingerprint: str
    key_id: str
    key_version: str
    topic: str
    partition: int
    offset: int


def verify_order_event(message, *, event_type, topic, keys) -> SignedOrderEvent:
    raw = message.value()
    if not isinstance(raw, bytes) or len(raw) > MAX_BYTES:
        raise OrderIngressError("MALFORMED_KAFKA_PAYLOAD")
    try:
        body = decode_kafka_payload(raw)
    except KafkaPayloadDecodeError:
        raise OrderIngressError("MALFORMED_KAFKA_PAYLOAD") from None
    auth = body.get("kafkaAuth")
    if auth is None and any(field not in body for field in ("eventVersion", "sourceService")):
        raise OrderIngressError("LEGACY_EVENT_METADATA_MISSING")
    if "eventVersion" not in body or "sourceService" not in body:
        raise OrderIngressError("EVENT_METADATA_MISSING")
    if type(body["eventVersion"]) is not int or body["eventVersion"] != 1:
        raise OrderIngressError("UNSUPPORTED_EVENT_VERSION")
    if body["sourceService"] != "order-service" or body.get("eventType") != event_type:
        raise OrderIngressError("UNEXPECTED_EVENT_SOURCE_OR_TYPE")
    if message.topic() != topic:
        raise OrderIngressError("UNEXPECTED_KAFKA_TOPIC")
    require_uuid(body.get("eventId"))
    order_id = require_uuid(body.get("orderId"))
    if body.get("customerId") is not None:
        require_uuid(body["customerId"])
    purchase = event_type == "ORDER_COMPLETED"
    record_key = order_id if purchase else require_uuid(body.get("returnId"))
    if _record_key(message.key()) != record_key or body.get("eventKey") != record_key:
        raise OrderIngressError("RECORD_KEY_MISMATCH")
    if not isinstance(auth, dict) or auth.keys() != {"keyId", "keyVersion", "signature"}:
        raise OrderIngressError("KAFKA_SIGNATURE_MISSING_OR_MALFORMED")
    key_id, version, signature = auth["keyId"], auth["keyVersion"], auth["signature"]
    if not all(isinstance(value, str) for value in (key_id, version, signature)):
        raise OrderIngressError("KAFKA_SIGNATURE_MISSING_OR_MALFORMED")
    if not TOKEN.fullmatch(key_id) or not TOKEN.fullmatch(version) or not re.fullmatch(r"[a-f0-9]{64}", signature):
        raise OrderIngressError("KAFKA_SIGNATURE_MISSING_OR_MALFORMED")
    key = keys.get((key_id, version))
    if key is None:
        raise OrderIngressError("UNKNOWN_KEY_ID" if not any(ref[0] == key_id for ref in keys) else "UNKNOWN_KEY_VERSION")
    try:
        data = _node({name: value for name, value in body.items() if name != "kafkaAuth"})
        context = b"".join(_text(b"s", value) for value in (POLICY, topic, record_key))
        signature_context = _text(b"s", key_id) + _text(b"s", version)
        expected = hmac.digest(key, context + signature_context + data, "sha256").hex()
    except (UnicodeError, RecursionError):
        raise OrderIngressError("MALFORMED_CANONICAL_PAYLOAD") from None
    if not hmac.compare_digest(expected, signature):
        raise OrderIngressError("INVALID_KAFKA_SIGNATURE")

    expected_headers = {
        "event_id": body["eventId"], "event_type": event_type, "event_version": "1",
        "source_service": body["sourceService"], "aggregate_type": "ORDER" if purchase else "ORDER_RETURN",
        "aggregate_id": record_key, "business_key": record_key + (":order-completed" if purchase else ":return-completed"),
    }
    optional = {name: body.get(field) for name, field in (
        ("saga_id", "sagaId"), ("correlation_id", "correlationId"), ("causation_id", "causationId")
    )}
    headers = {}
    try:
        for name, value in message.headers() or ():
            if name not in CONTRACT_HEADERS:
                continue
            if name in headers:
                raise OrderIngressError("DUPLICATE_KAFKA_HEADER")
            headers[name] = value.decode("utf-8") if isinstance(value, bytes) else value
    except UnicodeError:
        raise OrderIngressError("MALFORMED_KAFKA_HEADER") from None
    if not expected_headers.keys() <= headers.keys():
        raise OrderIngressError("KAFKA_HEADER_MISSING")
    if any(headers[name] != value for name, value in expected_headers.items()) or any(
        name in headers and headers[name] != value for name, value in optional.items()
    ):
        raise OrderIngressError("KAFKA_HEADER_BODY_MISMATCH")
    partition, offset = message.partition(), message.offset()
    if type(partition) is not int or type(offset) is not int or partition < 0 or offset < 0:
        raise OrderIngressError("INVALID_KAFKA_RECORD_LOCATION")
    return SignedOrderEvent(body, raw, list(headers.items()), hashlib.sha256(context + data).hexdigest(),
                            key_id, version, topic, partition, offset)
