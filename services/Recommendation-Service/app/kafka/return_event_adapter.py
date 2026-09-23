from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import logging
from typing import Any

from app.schemas.interaction import CanonicalInteraction

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
    def _parse_occurred_at(val: Any) -> datetime:
        if not val:
            raise ReturnEventValidationError("Missing required field 'occurredAt'")
        if isinstance(val, datetime):
            return val
        if isinstance(val, (int, float)):
            if val > 1e11:
                return datetime.fromtimestamp(val / 1000.0, tz=timezone.utc)
            return datetime.fromtimestamp(val, tz=timezone.utc)
        if isinstance(val, str):
            try:
                clean_str = val.replace("Z", "+00:00")
                return datetime.fromisoformat(clean_str)
            except Exception as e:
                raise ReturnEventValidationError(f"Invalid 'occurredAt' timestamp format: {val}") from e
        raise ReturnEventValidationError(f"Unsupported 'occurredAt' type: {type(val)}")

    def to_canonical_interactions(self, payload: dict[str, Any]) -> list[CanonicalInteraction]:
        if not isinstance(payload, dict):
            raise ReturnEventValidationError("Kafka payload must be a JSON dictionary")

        event_id = payload.get("eventId")
        if not event_id or not str(event_id).strip():
            raise ReturnEventValidationError("Missing or empty required field 'eventId'")

        event_type = payload.get("eventType")
        if event_type != "RETURN_COMPLETED":
            raise ReturnEventValidationError(
                f"Invalid eventType '{event_type}', expected 'RETURN_COMPLETED'"
            )

        new_status = payload.get("newStatus")
        if new_status != "COMPLETED":
            raise ReturnEventValidationError(
                f"Invalid newStatus '{new_status}', expected 'COMPLETED'"
            )

        return_id = payload.get("returnId")
        if not return_id or not str(return_id).strip():
            raise ReturnEventValidationError("Missing or empty required field 'returnId'")

        order_id = payload.get("orderId")
        if not order_id or not str(order_id).strip():
            raise ReturnEventValidationError("Missing or empty required field 'orderId'")

        customer_id_raw = payload.get("customerId")
        customer_id = str(customer_id_raw).strip() if customer_id_raw and str(customer_id_raw).strip() else None

        event_time = self._parse_occurred_at(payload.get("occurredAt"))

        raw_items = payload.get("items")
        if not isinstance(raw_items, list) or len(raw_items) == 0:
            raise ReturnEventValidationError("Missing or empty 'items' array in return.completed event")

        canonical_interactions: list[CanonicalInteraction] = []

        for idx, item in enumerate(raw_items):
            if not isinstance(item, dict):
                raise ReturnEventValidationError(f"Item at index {idx} is not a valid JSON object")

            return_item_id = item.get("returnItemId")
            if not return_item_id or not str(return_item_id).strip():
                raise ReturnEventValidationError(f"Item at index {idx} missing required 'returnItemId'")

            order_item_id = item.get("orderItemId")
            if not order_item_id or not str(order_item_id).strip():
                raise ReturnEventValidationError(f"Item at index {idx} missing required 'orderItemId'")

            product_variant_id_raw = item.get("productVariantId")
            product_variant_id = (
                str(product_variant_id_raw).strip()
                if product_variant_id_raw and str(product_variant_id_raw).strip()
                else None
            )

            sku_raw = item.get("sku")
            sku = str(sku_raw).strip() if sku_raw and str(sku_raw).strip() else None

            if not product_variant_id and not sku:
                raise ReturnEventValidationError(
                    f"Item at index {idx} (orderItemId='{order_item_id}') must provide at least one product identifier ('productVariantId' or 'sku')"
                )

            quantity_raw = item.get("quantity")
            if quantity_raw is None:
                raise ReturnEventValidationError(f"Item at index {idx} missing required 'quantity'")
            try:
                quantity = Decimal(str(quantity_raw))
            except (InvalidOperation, ValueError) as e:
                raise ReturnEventValidationError(f"Item at index {idx} has invalid quantity '{quantity_raw}'") from e

            if quantity <= 0:
                raise ReturnEventValidationError(f"Item at index {idx} has non-positive quantity '{quantity}'")

            return_reason_raw = item.get("returnReason")
            if not return_reason_raw or not str(return_reason_raw).strip():
                raise ReturnEventValidationError(f"Item at index {idx} missing required 'returnReason'")
            return_reason = str(return_reason_raw).strip().upper()

            # Reason filtering: only CHANGE_OF_MIND generates preference-negative RETURN interaction
            if return_reason in PREFERENCE_NEGATIVE_REASONS:
                interaction = CanonicalInteraction(
                    source_event_id=str(event_id).strip(),
                    order_item_id=str(order_item_id).strip(),
                    return_item_id=str(return_item_id).strip(),
                    customer_id=customer_id,
                    product_variant_id=product_variant_id,
                    sku=sku,
                    event_type="RETURN",
                    quantity=quantity,
                    order_id=str(order_id).strip(),
                    return_id=str(return_id).strip(),
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
                    f"Item at index {idx} has unsupported returnReason '{return_reason}'"
                )

        return canonical_interactions
