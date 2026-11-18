from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import logging
from typing import Any
from uuid import UUID

from app.schemas.interaction import CanonicalInteraction
from app.kafka.numeric_time import decimal_epoch

logger = logging.getLogger(__name__)

# Only CHANGE_OF_MIND represents customer preference-negative feedback.
PREFERENCE_NEGATIVE_REASONS = {"CHANGE_OF_MIND"}

# Operational, logistics, manufacturing, and fulfillment reasons that must not affect preference scoring.
OPERATIONAL_RETURN_REASONS = {
    "DEFECTIVE",
    "DAMAGED",
    "WRONG_ITEM",
    "QUALITY_ISSUE",
    "EXPIRED",
    "OTHER",
}


class ReturnEventValidationError(Exception):
    """Raised when a notification.return.completed Kafka payload fails schema or business state validation."""


class ReturnEventAdapter:
    """
    Normalizes decoded 'notification.return.completed' Kafka events into CanonicalInteraction models.
    Filters out operational returns so only preference-negative returns generate recommendation interactions.
    Decoupled from Kafka consumers and database sessions.
    """

    @staticmethod
    def _identity(value: Any, field: str) -> str:
        """Keep upstream UUID identity; never manufacture keys or coerce objects."""
        if not isinstance(value, str) or not value.strip():
            raise ReturnEventValidationError(f"Missing or invalid identity '{field}'")
        cleaned = value.strip()
        try:
            identity = str(UUID(cleaned))
        except ValueError as exc:
            raise ReturnEventValidationError(f"Invalid UUID identity '{field}'") from exc
        if identity != cleaned.lower():
            raise ReturnEventValidationError(f"Invalid UUID identity '{field}'")
        return identity

    @staticmethod
    def _validate_contract_metadata(payload: dict[str, Any]) -> None:
        # RETURN producers do not currently include these additive body fields.
        # Accept their absence, but never accept an explicit unsupported contract.
        version = payload.get("eventVersion", 1)
        if type(version) is not int or version != 1:
            raise ReturnEventValidationError("Unsupported eventVersion")
        if payload.get("sourceService", "order-service") != "order-service":
            raise ReturnEventValidationError("Unexpected sourceService")

    @classmethod
    def validate_headers(cls, payload: dict[str, Any], headers: list | tuple | None) -> None:
        """Validate supplied EventRouter headers without requiring legacy headers."""
        cls._validate_contract_metadata(payload)
        if headers is None:
            return
        if not isinstance(headers, (list, tuple)):
            raise ReturnEventValidationError("Kafka headers must be a sequence of pairs")
        if payload.get("eventType") != "RETURN_COMPLETED":
            raise ReturnEventValidationError("Header contract requires eventType 'RETURN_COMPLETED'")
        event_id = cls._identity(payload.get("eventId"), "eventId")
        return_id = cls._identity(payload.get("returnId"), "returnId")
        expected = {
            "event_id": event_id,
            "event_type": "RETURN_COMPLETED",
            "event_version": "1",
            "source_service": "order-service",
            "aggregate_type": "ORDER_RETURN",
            "aggregate_id": return_id,
            "business_key": return_id + ":return-completed",
            "saga_id": payload.get("sagaId"),
            "correlation_id": payload.get("correlationId", return_id),
            "causation_id": payload.get("causationId"),
        }
        seen: set[str] = set()
        for header in headers:
            if (not isinstance(header, (list, tuple)) or len(header) != 2
                    or not isinstance(header[0], str)):
                raise ReturnEventValidationError("Kafka headers must contain name/value pairs")
            key, value = header
            if key not in expected:
                continue  # Unrelated tracing headers are outside this contract.
            if key in seen:
                raise ReturnEventValidationError(f"Duplicate contract header: {key}")
            seen.add(key)
            if isinstance(value, bytes):
                try:
                    value = value.decode("utf-8")
                except UnicodeDecodeError as exc:
                    raise ReturnEventValidationError("Invalid contract header encoding") from exc
            elif value is not None and not isinstance(value, str):
                raise ReturnEventValidationError(f"Invalid contract header value: {key}")
            if value != expected[key]:
                raise ReturnEventValidationError(f"Contract header/body mismatch: {key}")

    @staticmethod
    def _parse_occurred_at(val: Any) -> datetime:
        if val is None or val == "":
            raise ReturnEventValidationError("Missing required field 'occurredAt'")
        try:
            if isinstance(val, bool):
                raise ValueError("Boolean timestamp")
            if isinstance(val, datetime):
                parsed = val
            elif isinstance(val, Decimal):
                parsed = decimal_epoch(val)
            elif isinstance(val, (int, float)):
                # Preserve the existing seconds/milliseconds transport convention.
                parsed = datetime.fromtimestamp(val / 1000.0 if val > 1e11 else val, tz=timezone.utc)
            elif isinstance(val, str):
                parsed = datetime.fromisoformat(val.replace("Z", "+00:00"))
            else:
                raise ValueError("Unsupported timestamp type")
            if parsed.tzinfo is None or parsed.utcoffset() is None:
                raise ReturnEventValidationError("occurredAt must include a timezone")
            return parsed.astimezone(timezone.utc)
        except (ValueError, OverflowError, OSError) as exc:
            raise ReturnEventValidationError("Invalid occurredAt timestamp") from exc

    @staticmethod
    def _quantity(value: Any, index: int) -> Decimal:
        try:
            quantity = Decimal(str(value))
        except (InvalidOperation, ValueError) as exc:
            raise ReturnEventValidationError(f"Item at index {index} has invalid quantity") from exc
        if not quantity.is_finite() or quantity <= 0:
            raise ReturnEventValidationError(f"Item at index {index} quantity must be finite and positive")
        # Check the supplied scale, including trailing zeros; never round/clamp.
        if quantity.adjusted() >= 11 or quantity.as_tuple().exponent < -3:
            raise ReturnEventValidationError("quantity exceeds the persisted DECIMAL(14,3) contract")
        return quantity

    def to_canonical_interactions(self, payload: dict[str, Any]) -> list[CanonicalInteraction]:
        if not isinstance(payload, dict):
            raise ReturnEventValidationError("Kafka payload must be a JSON dictionary")

        event_id = self._identity(payload.get("eventId"), "eventId")
        self._validate_contract_metadata(payload)

        event_type = payload.get("eventType")
        if event_type != "RETURN_COMPLETED":
            raise ReturnEventValidationError(
                "Invalid eventType, expected RETURN_COMPLETED"
            )

        new_status = payload.get("newStatus")
        if new_status != "COMPLETED":
            raise ReturnEventValidationError(
                "Invalid newStatus, expected COMPLETED"
            )

        return_id = self._identity(payload.get("returnId"), "returnId")
        order_id = self._identity(payload.get("orderId"), "orderId")
        customer_id_raw = payload.get("customerId")
        customer_id = self._identity(customer_id_raw, "customerId") if customer_id_raw is not None else None

        event_time = self._parse_occurred_at(payload.get("occurredAt"))

        raw_items = payload.get("items")
        if not isinstance(raw_items, list) or len(raw_items) == 0:
            raise ReturnEventValidationError("Missing or empty 'items' array in return.completed event")

        canonical_interactions: list[CanonicalInteraction] = []
        seen_return_items: set[str] = set()
        seen_order_items: set[str] = set()

        for idx, item in enumerate(raw_items):
            if not isinstance(item, dict):
                raise ReturnEventValidationError(f"Item at index {idx} is not a valid JSON object")

            return_item_id = self._identity(item.get("returnItemId"), "returnItemId")
            order_item_id = self._identity(item.get("orderItemId"), "orderItemId")
            if return_item_id in seen_return_items:
                raise ReturnEventValidationError("Duplicate returnItemId in return event")
            if order_item_id in seen_order_items:
                raise ReturnEventValidationError("Duplicate orderItemId in return event")
            seen_return_items.add(return_item_id)
            seen_order_items.add(order_item_id)

            product_variant_id_raw = item.get("productVariantId")
            product_variant_id = (
                self._identity(product_variant_id_raw, "productVariantId")
                if product_variant_id_raw is not None
                else None
            )

            sku_raw = item.get("sku")
            if sku_raw is not None and (
                not isinstance(sku_raw, str) or not sku_raw.strip() or len(sku_raw.strip()) > 100
            ):
                raise ReturnEventValidationError("sku must be a nonblank string of at most 100 characters")
            sku = sku_raw.strip() if sku_raw is not None else None

            if not product_variant_id and not sku:
                raise ReturnEventValidationError(
                    f"Item at index {idx} (orderItemId='{order_item_id}') must provide at least one product identifier ('productVariantId' or 'sku')"
                )

            quantity_raw = item.get("quantity")
            if quantity_raw is None:
                raise ReturnEventValidationError(f"Item at index {idx} missing required 'quantity'")
            quantity = self._quantity(quantity_raw, idx)

            return_reason_raw = item.get("returnReason")
            if not isinstance(return_reason_raw, str) or not return_reason_raw.strip():
                raise ReturnEventValidationError(f"Item at index {idx} missing required 'returnReason'")
            return_reason = return_reason_raw.strip().upper()

            # Reason filtering: only CHANGE_OF_MIND generates preference-negative RETURN interaction
            if return_reason in PREFERENCE_NEGATIVE_REASONS:
                interaction = CanonicalInteraction(
                    source_event_id=event_id,
                    order_item_id=order_item_id,
                    return_item_id=return_item_id,
                    customer_id=customer_id,
                    product_variant_id=product_variant_id,
                    sku=sku,
                    event_type="RETURN",
                    quantity=quantity,
                    order_id=order_id,
                    return_id=return_id,
                    return_reason=return_reason,
                    event_time=event_time,
                    metadata={
                        "returnCode": payload.get("returnCode"),
                        "orderCode": payload.get("orderCode"),
                    },
                )
                canonical_interactions.append(interaction)
            elif return_reason in OPERATIONAL_RETURN_REASONS:
                logger.info(
                    "Skipping operational/non-preference return item: returnItemId=%s, orderItemId=%s, reason=%s",
                    return_item_id,
                    order_item_id,
                    return_reason,
                )
            else:
                raise ReturnEventValidationError(
                    f"Item at index {idx} has unsupported returnReason"
                )

        return canonical_interactions
