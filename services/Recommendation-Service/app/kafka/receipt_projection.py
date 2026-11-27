"""Allowlisted approved raw, not Information, a validator verdict, or a DB inbox.

Replay is deliberately limited to retained validator dependencies.
Unclassified or invalid inputs are diagnostic-only.
"""
from dataclasses import dataclass
from decimal import Decimal
import re
from uuid import UUID

from app.kafka.payload_decoder import KafkaPayloadDecodeError, decode_kafka_payload
from app.kafka.receipt_canonical import (
    ReceiptPolicyError, json_bytes, keyed_digest, pointer, typed_tree,
)

ALLOWLIST_POLICY = "hvtra-approved-receipt-v1"
CONTRACT_HEADERS = frozenset({"event_id", "event_type", "event_version", "source_service",
                              "aggregate_type", "aggregate_id", "business_key", "saga_id",
                              "correlation_id", "causation_id"})
COMMON = {"eventId", "eventType", "eventKey", "orderId", "customerId", "occurredAt", "items",
          "sagaId", "correlationId", "causationId", "eventVersion", "sourceService"}
PURCHASE = COMMON | {"paymentStatus", "orderStatus", "completedAt"}
RETURN = COMMON | {"returnId", "oldStatus", "newStatus"}
PURCHASE_LINE = {"orderItemId", "purchaseType", "itemType", "productVariantId", "productId",
                 "sku", "quantity", "unitPrice", "bomVersionId", "components"}
COMPONENT = {"ingredientId", "ingredientCode", "quantity", "unit"}
RETURN_LINE = {"returnItemId", "orderItemId", "productVariantId", "sku", "quantity",
               "returnReason", "condition"}
DROP_COMMON = {"orderCode", "userId", "kafkaAuth"}
DROP_PURCHASE = DROP_COMMON | {"paymentMethod", "paymentCode", "finalAmount", "paidAmount",
    "discountAmount", "usageIds", "originalAmount", "customerName", "customerEmail",
    "customerPhone", "cashierName", "branchName"}
DROP_RETURN = DROP_COMMON | {"returnCode", "branchId", "actorUserId", "paymentId",
                           "refundTransactionId", "title", "message"}
DROP_PURCHASE_LINE = {"productName", "variantName", "originalUnitPrice", "discountAmount", "lineTotal"}
DROP_RETURN_LINE = {"productName", "note"}
IDENTITIES = {"eventId", "eventKey", "orderId", "customerId", "returnId", "sagaId",
              "orderItemId", "returnItemId", "productVariantId",
              "productId", "bomVersionId", "ingredientId"}
TRACE_TOKENS = {"correlationId", "causationId"}
ENUMS = {
    "eventType": {"ORDER_COMPLETED", "RETURN_COMPLETED"},
    "sourceService": {"order-service"},
    "purchaseType": {"PRODUCT", "CUSTOM_BUNDLE", "CUSTOM_BOM"},
    "itemType": {"PRODUCT", "CUSTOM_BUNDLE", "CUSTOM_BOM"},
    "paymentStatus": {"PAID", "UNPAID", "PARTIALLY_PAID", "REFUNDED", "PARTIALLY_REFUNDED", "PENDING", "FAILED"},
    "orderStatus": {"COMPLETED", "PAYMENT_CONFIRMED", "PENDING_PAYMENT", "CANCELLED", "EXPIRED", "PAYMENT_FAILED"},
    "oldStatus": {"CREATED", "RECEIVED", "COMPLETED", "CANCELLED"},
    "newStatus": {"CREATED", "RECEIVED", "COMPLETED", "CANCELLED"},
    "returnReason": {"CHANGE_OF_MIND", "DEFECTIVE", "DAMAGED", "WRONG_ITEM", "QUALITY_ISSUE", "EXPIRED", "OTHER"},
    "condition": {"SEALED", "OPENED", "DAMAGED", "SPOILED", "USED"},
}


@dataclass(frozen=True)
class ApprovedReceipt:
    """JSON-compatible typed document; diagnostics contain no original values."""
    document: dict

    @property
    def replay_level(self):
        return self.document["replay_level"]


def _uuid_text(value):
    if not isinstance(value, str):
        return False
    try:
        return str(UUID(value.strip())) == value.strip().lower()
    except ValueError:
        return False


def _safe_value(field, value):
    if value is None:
        return True  # Preserve explicit null, including invalid required-field null.
    if field in IDENTITIES:
        return _uuid_text(value)
    if field in TRACE_TOKENS:
        return isinstance(value, str) and bool(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,149}", value))
    if field in ("quantity", "unitPrice"):
        if type(value) in (int, bool) or isinstance(value, Decimal):
            return not isinstance(value, Decimal) or value.is_finite()
        return isinstance(value, str) and len(value) <= 128 and bool(re.fullmatch(
            r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?", value))
    if field == "eventVersion":
        return type(value) in (int, bool) or isinstance(value, Decimal)
    if field in ENUMS:
        if not isinstance(value, str):
            return False
        return (value.strip().upper() if field == "returnReason" else value) in ENUMS[field]
    if field in ("sku", "ingredientCode"):
        return isinstance(value, str) and bool(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}", value))
    if field == "unit":
        return isinstance(value, str) and bool(re.fullmatch(r"[A-Za-z0-9µμ][A-Za-z0-9µμ._-]{0,23}", value))
    if field in ("occurredAt", "completedAt"):
        return (type(value) in (int, bool) or isinstance(value, Decimal)
                or isinstance(value, str) and len(value) <= 64
                and bool(re.fullmatch(r"[0-9TtZz +:.,-]+", value)))
    return False


def project_receipt(raw_value: bytes | str, headers=None, *, keys, key_version: str,
                    policy_version=ALLOWLIST_POLICY) -> ApprovedReceipt:
    """Capture approved fields before reason filtering; no claim of domain validity.

    Even bad JSON gets a controlled diagnostic. HMAC dependencies fail closed;
    key failures never become a successful quarantine outcome.
    """
    if policy_version != ALLOWLIST_POLICY:
        raise ReceiptPolicyError("UNSUPPORTED_ALLOWLIST_POLICY")
    if not isinstance(raw_value, (bytes, str)):
        raise ReceiptPolicyError("RAW_BYTES_OR_TEXT_REQUIRED")
    diagnostics = []

    def diagnostic(code, path, value):
        try:
            tree = typed_tree(value)
        except (ReceiptPolicyError, RecursionError):
            tree = ["unsupported-type", type(value).__name__]
        evidence = keyed_digest("redacted-value", tree, keys=keys,
                                key_version=key_version, policy_version=policy_version)
        diagnostics.append({"code": code, "path": path, "value_kind": type(value).__name__,
                            "value_hmac": evidence.value, "key_version": key_version})

    # Validate key availability even when every value is already allowed.
    keyed_digest("key-check", [], keys=keys, key_version=key_version, policy_version=policy_version)
    try:
        source = decode_kafka_payload(raw_value)
    except KafkaPayloadDecodeError:
        return ApprovedReceipt({"policy_version": policy_version, "replay_level": "DIAGNOSTIC_ONLY",
                                "payload": None, "headers": [], "transport_kind": "unreadable",
                                "diagnostics": [{"code": "STRICT_DECODE_FAILED", "path": ""}]})
    is_purchase = source.get("eventType") == "ORDER_COMPLETED"
    if source.get("eventType") not in ("ORDER_COMPLETED", "RETURN_COMPLETED"):
        diagnostic("UNKNOWN_SCHEMA", "/eventType", source.get("eventType"))
        return ApprovedReceipt({"policy_version": policy_version, "replay_level": "DIAGNOSTIC_ONLY",
                                "payload": None, "headers": [], "transport_kind": source.transport_kind,
                                "diagnostics": diagnostics})

    def select(obj, keep, drop, path):
        if not isinstance(obj, dict):
            diagnostic("INVALID_OBJECT", path, obj)
            return None
        selected = {}
        for field, value in obj.items():
            if field in drop:
                continue
            if field not in keep:
                # Unknown key names themselves may be PII.
                diagnostic("UNCLASSIFIED_FIELD", path + "/*", {field: value})
                continue
            location = pointer(path, field)
            if field == "items":
                if not isinstance(value, list):
                    diagnostic("INVALID_ITEMS", location, value)
                else:
                    selected[field] = [select(v, PURCHASE_LINE if is_purchase else RETURN_LINE,
                                              DROP_PURCHASE_LINE if is_purchase else DROP_RETURN_LINE,
                                              location + "/" + str(i)) for i, v in enumerate(value)]
            elif field == "components" and value is not None:
                if not isinstance(value, list):
                    diagnostic("INVALID_COMPONENTS", location, value)
                else:
                    selected[field] = [select(v, COMPONENT, set(), location + "/" + str(i))
                                       for i, v in enumerate(value)]
            elif _safe_value(field, value):
                selected[field] = value
            else:
                diagnostic("REDACTED_INVALID_VALUE", location, value)
        return selected

    selected = select(source, PURCHASE if is_purchase else RETURN,
                      DROP_PURCHASE if is_purchase else DROP_RETURN, "")
    if "eventVersion" in source and (type(source["eventVersion"]) is not int or source["eventVersion"] != 1):
        diagnostic("UNSUPPORTED_VERSION", "/eventVersion", source["eventVersion"])
    kept_headers = []
    if headers is not None and not isinstance(headers, (list, tuple)):
        diagnostic("INVALID_HEADERS", "/headers", headers)
    else:
        for pair in headers or []:
            if not isinstance(pair, (list, tuple)) or len(pair) != 2 or not isinstance(pair[0], str):
                diagnostic("INVALID_HEADER_PAIR", "/headers/*", list(pair) if isinstance(pair, tuple) else pair)
                continue
            name, value = pair
            if name not in CONTRACT_HEADERS:
                continue  # Unrelated transport headers do not affect current validators.
            if isinstance(value, bytes):
                try:
                    value = value.decode("utf-8")
                except UnicodeDecodeError:
                    # bytes represented only inside the keyed diagnostic preimage.
                    diagnostic("INVALID_HEADER_ENCODING", "/headers/" + name, value.hex())
                    continue
            field = {"event_id": "eventId", "aggregate_id": "returnId", "saga_id": "sagaId",
                     "correlation_id": "correlationId", "causation_id": "causationId"}.get(name)
            safe = value is None or (field and _safe_value(field, value))
            safe = safe or (name == "event_version" and isinstance(value, str) and value.isascii()
                            and value.isdigit() and len(value) <= 8)
            safe = safe or (name == "event_type" and value in ("ORDER_COMPLETED", "RETURN_COMPLETED"))
            safe = safe or (name == "source_service" and value == "order-service")
            safe = safe or (name == "aggregate_type" and value in ("ORDER", "ORDER_RETURN"))
            safe = safe or (name == "business_key" and isinstance(value, str)
                            and bool(re.fullmatch(r"[0-9a-fA-F-]{36}:(?:order|return)-completed", value)))
            if safe:
                kept_headers.append([name, value])
            else:
                diagnostic("REDACTED_HEADER_VALUE", "/headers/" + name, value)
    return ApprovedReceipt({"policy_version": policy_version,
        "replay_level": "DIAGNOSTIC_ONLY" if diagnostics else "POLICY_PROJECTION",
        "payload": typed_tree(selected, source.numeric_tokens), "headers": kept_headers,
        "headers_present": headers is not None, "transport_kind": source.transport_kind,
        "diagnostics": diagnostics})


def replay_projection(receipt: ApprovedReceipt):
    """Reconstruct the preserved business input, never a corrected or full raw body."""
    document = receipt.document
    if document.get("policy_version") != ALLOWLIST_POLICY or receipt.replay_level != "POLICY_PROJECTION":
        raise ReceiptPolicyError("RECEIPT_NOT_REPLAYABLE")

    def emit(node):
        kind, *content = node
        if kind == "null":
            return b"null"
        value, = content
        if kind in ("integer", "decimal"):
            return value.encode("ascii")
        if kind in ("bool", "string"):
            return json_bytes(value)
        if kind == "array":
            return b"[" + b",".join(emit(v) for v in value) + b"]"
        if kind == "object":
            return b"{" + b",".join(json_bytes(k) + b":" + emit(v) for k, v in value) + b"}"
        raise ReceiptPolicyError("INVALID_REPLAY_DOCUMENT")

    try:
        return decode_kafka_payload(emit(document["payload"]))
    except (ValueError, TypeError, KeyError, UnicodeError):
        raise ReceiptPolicyError("INVALID_REPLAY_DOCUMENT") from None
