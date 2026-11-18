"""Pure hash/identity proposals; no production keys, dedup decisions, or DB writes."""
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
import hmac
from uuid import UUID

from app.kafka.payload_decoder import decode_kafka_payload
from app.kafka.receipt_canonical import (
    CANONICAL_POLICY, ReceiptPolicyError, keyed_digest, policy_name, sha256_digest, typed_tree,
)
from app.kafka.receipt_projection import ApprovedReceipt, CONTRACT_HEADERS, ENUMS, replay_projection

WIRE_POLICY = "hvtra-wire-frame-v1"
PAYLOAD_POLICY = "hvtra-approved-hash-v1"
BUSINESS_POLICY = "hvtra-business-content-v1"


def _transport_atom(value):
    if value is None:
        return ["null"]
    if isinstance(value, bytes):
        return ["bytes", value.hex()]
    if isinstance(value, str):
        return ["text", value]
    raise ReceiptPolicyError("INVALID_TRANSPORT_TYPE")


def _header_frame(headers, *, known_only=False):
    if headers is None:
        return ["absent"]
    if not isinstance(headers, (list, tuple)):
        raise ReceiptPolicyError("INVALID_HEADERS_SHAPE")
    entries = []
    for pair in headers:
        if not isinstance(pair, (list, tuple)) or len(pair) != 2 or not isinstance(pair[0], str):
            raise ReceiptPolicyError("INVALID_HEADERS_SHAPE")
        name, value = pair
        if known_only and name not in CONTRACT_HEADERS:
            continue
        if known_only and isinstance(value, bytes):
            try:
                value = value.decode("utf-8")
            except UnicodeDecodeError:
                raise ReceiptPolicyError("INVALID_HEADER_ENCODING") from None
        entries.append([name, _transport_atom(value)])
    return entries


def wire_fingerprint(raw_value, *, record_key=None, headers=None, keys, key_version,
                     policy_version=WIRE_POLICY):
    if policy_version != WIRE_POLICY:
        raise ReceiptPolicyError("UNSUPPORTED_WIRE_POLICY")
    return keyed_digest("wire", [_transport_atom(record_key), _transport_atom(raw_value),
                                  _header_frame(headers)], keys=keys, key_version=key_version,
                        policy_version=policy_version)


def event_integrity(raw_value, *, headers=None, keys, key_version,
                    policy_version=CANONICAL_POLICY):
    if policy_version != CANONICAL_POLICY:
        raise ReceiptPolicyError("UNSUPPORTED_EVENT_POLICY")
    if not isinstance(raw_value, (bytes, str)):
        raise ReceiptPolicyError("RAW_BYTES_OR_TEXT_REQUIRED")
    payload = decode_kafka_payload(raw_value)
    # Excludes transport wrapper, includes every business body field in RAM,
    # including fields that the approved projection will redact.
    return keyed_digest("event-integrity", [typed_tree(payload, payload.numeric_tokens),
                                            _header_frame(headers, known_only=True)],
                        keys=keys, key_version=key_version, policy_version=policy_version)


def approved_payload_hash(receipt: ApprovedReceipt, *, policy_version=PAYLOAD_POLICY):
    if policy_version != PAYLOAD_POLICY:
        raise ReceiptPolicyError("UNSUPPORTED_PAYLOAD_HASH_POLICY")
    return sha256_digest("approved-payload", receipt.document, policy_version=policy_version)


def verified_replay(receipt: ApprovedReceipt, expected_digest: str):
    if not hmac.compare_digest(approved_payload_hash(receipt).value, expected_digest):
        raise ReceiptPolicyError("PAYLOAD_HASH_MISMATCH")
    return replay_projection(receipt)


@dataclass(frozen=True)
class EventIdentity:
    namespace: str
    source: str
    event_id: str
    event_type: str
    version: int
    source_inferred: bool
    version_inferred: bool

    @property
    def key(self):
        # eventType is checked content, never an extra uniqueness dimension.
        return self.namespace, self.source, self.event_id


@dataclass(frozen=True)
class BusinessClaim:
    namespace: str
    source: str
    kind: str
    identity: str
    content_hash: str
    policy_version: str = BUSINESS_POLICY

    @property
    def key(self):
        return self.namespace, self.source, self.kind, self.identity


def _uuid(value):
    if not isinstance(value, str):
        raise ReceiptPolicyError("INVALID_BUSINESS_IDENTITY")
    try:
        canonical = str(UUID(value.strip()))
    except ValueError:
        raise ReceiptPolicyError("INVALID_BUSINESS_IDENTITY") from None
    if canonical != value.strip().lower():
        raise ReceiptPolicyError("INVALID_BUSINESS_IDENTITY")
    return canonical


def _numeric_value(value):
    """Context-independent exact decimal value equality, ignoring supplied scale."""
    number = value if isinstance(value, Decimal) else Decimal(str(value))
    if not number.is_finite():
        raise ReceiptPolicyError("INVALID_BUSINESS_NUMBER")
    sign, digits, exponent = number.as_tuple()
    digits = list(digits)
    if not any(digits):
        return "0"
    while digits[-1] == 0:
        digits.pop()
        exponent += 1
    return ("-" if sign else "") + "".join(map(str, digits)) + "e" + str(exponent)


def event_identity(raw_value, *, namespace: str) -> EventIdentity:
    """Identity descriptor only; inferred legacy metadata never modifies the body."""
    policy_name(namespace)
    if not isinstance(raw_value, (bytes, str)):
        raise ReceiptPolicyError("RAW_BYTES_OR_TEXT_REQUIRED")
    body = decode_kafka_payload(raw_value)
    version = body.get("eventVersion", 1)
    source = body.get("sourceService", "order-service")
    if (type(version) is not int or version != 1 or source != "order-service"
            or body.get("eventType") not in ("ORDER_COMPLETED", "RETURN_COMPLETED")):
        raise ReceiptPolicyError("UNSUPPORTED_EVENT_CONTRACT")
    return EventIdentity(namespace, source, _uuid(body.get("eventId")), body["eventType"], version,
                         "sourceService" not in body, "eventVersion" not in body)


def business_claims(raw_value, *, namespace: str, headers=None,
                    policy_version=BUSINESS_POLICY) -> tuple[BusinessClaim, ...]:
    """Validated content candidates, not a claim that purchase/return is reconciled.

    UUID authority syntax is required for this future helper. The runtime adapter
    contract and resolver remain independent. No Catalog calls or preference fit.
    """
    if policy_version != BUSINESS_POLICY:
        raise ReceiptPolicyError("UNSUPPORTED_BUSINESS_POLICY")
    policy_name(namespace)
    if not isinstance(raw_value, (bytes, str)):
        raise ReceiptPolicyError("RAW_BYTES_OR_TEXT_REQUIRED")
    body = decode_kafka_payload(raw_value)
    purchase = body.get("eventType") == "ORDER_COMPLETED"
    if body.get("eventType") not in ("ORDER_COMPLETED", "RETURN_COMPLETED"):
        raise ReceiptPolicyError("UNSUPPORTED_BUSINESS_EVENT")
    # UUID/type checks before invoking legacy adapters prevent unsafe ID coercion.
    _uuid(body.get("eventId"))
    order_id = _uuid(body.get("orderId"))
    customer = _uuid(body["customerId"]) if body.get("customerId") is not None else None
    return_id = None if purchase else _uuid(body.get("returnId"))
    if not isinstance(body.get("items"), list) or not body["items"]:
        raise ReceiptPolicyError("INVALID_BUSINESS_LINES")
    for line in body["items"]:
        if not isinstance(line, dict):
            raise ReceiptPolicyError("INVALID_BUSINESS_LINES")
        for field in ("orderItemId", "returnItemId", "productVariantId", "productId", "bomVersionId"):
            if field in line and line[field] is not None:
                _uuid(line[field])
        _uuid(line.get("orderItemId"))
        if not purchase:
            _uuid(line.get("returnItemId"))
            if line.get("condition") is not None and (not isinstance(line["condition"], str)
                                                      or line["condition"] not in ENUMS["condition"]):
                raise ReceiptPolicyError("INVALID_RETURN_CONDITION")
    # Imported only here; both adapters are pure and do not open DB sessions.
    from app.kafka.purchase_event_adapter import PurchaseEventAdapter
    from app.kafka.return_event_adapter import ReturnEventAdapter, ReturnEventValidationError
    adapter = PurchaseEventAdapter() if purchase else ReturnEventAdapter()
    try:
        adapter.validate_headers(body, headers)
        interactions = adapter.to_canonical_interactions(body)
        business_time = (interactions[0].event_time if interactions else
                         adapter._parse_occurred_at(body.get("occurredAt")))
    except (ValueError, TypeError, InvalidOperation, OverflowError, ReturnEventValidationError):
        raise ReceiptPolicyError("BUSINESS_CONTRACT_INVALID") from None
    from datetime import timezone
    time_value = business_time.astimezone(timezone.utc).isoformat(timespec="microseconds")
    source = "order-service"
    common = {"orderId": order_id, "customerId": customer, "businessTime": time_value,
              "returnId": return_id}
    normalized = []
    for line in body["items"]:
        value = dict(common, orderItemId=_uuid(line["orderItemId"]),
                     variantId=_uuid(line["productVariantId"]) if line.get("productVariantId") else None,
                     quantity=_numeric_value(line["quantity"]))
        if purchase:
            value.update(purchaseType="PRODUCT", unitPrice=_numeric_value(line["unitPrice"])
                         if line.get("unitPrice") is not None else None,
                         bomVersionId=_uuid(line["bomVersionId"]) if line.get("bomVersionId") else None)
        else:
            value.update(returnItemId=_uuid(line["returnItemId"]),
                         reason=line["returnReason"].strip().upper(), condition=line.get("condition"))
        # SKU is a binding dependency only when no authoritative variant exists.
        if not value["variantId"]:
            value["sku"] = line.get("sku")
        if line.get("productId") is not None:
            value["explicitProductId"] = _uuid(line["productId"])
        normalized.append(value)
    result = []

    def claim(kind, identity, content):
        digest = sha256_digest("business-content", [namespace, source, kind, identity, content],
                               policy_version=policy_version)
        result.append(BusinessClaim(namespace, source, kind, identity, digest.value, policy_version))

    for line in normalized:
        claim("PURCHASE_LINE" if purchase else "RETURN_LINE",
              line["orderItemId"] if purchase else line["returnItemId"], line)
    normalized.sort(key=lambda line: line["orderItemId"] if purchase else line["returnItemId"])
    claim("ORDER_COMPLETION" if purchase else "RETURN_COMPLETION", order_id if purchase else return_id,
          [common, normalized])
    # eventId has been validated but deliberately is not a business hash input.
    return tuple(sorted(result, key=lambda item: (item.kind, item.identity)))
