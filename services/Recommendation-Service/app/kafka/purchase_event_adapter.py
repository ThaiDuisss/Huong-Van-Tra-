from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import logging
from typing import Any

from app.schemas.interaction import CanonicalInteraction

logger = logging.getLogger(__name__)


class PurchaseEventValidationError(Exception):
    """Raised when an order.completed Kafka payload fails schema or business state validation."""


class PurchaseEventAdapter:
    """
    Normalizes decoded 'order.completed' Kafka events into CanonicalInteraction models.
    Decoupled from Kafka consumers and database sessions.
    """

    @staticmethod
    def _parse_occurred_at(val: Any) -> datetime:
        if not val:
            raise PurchaseEventValidationError("Missing required field 'occurredAt'")
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
                raise PurchaseEventValidationError(f"Invalid 'occurredAt' timestamp format: {val}") from e
        raise PurchaseEventValidationError(f"Unsupported 'occurredAt' type: {type(val)}")

    def to_canonical_interactions(self, payload: dict[str, Any]) -> list[CanonicalInteraction]:
        if not isinstance(payload, dict):
            raise PurchaseEventValidationError("Kafka payload must be a JSON dictionary")

        event_id = payload.get("eventId")
        if not event_id or not str(event_id).strip():
            raise PurchaseEventValidationError("Missing or empty required field 'eventId'")

        event_type = payload.get("eventType")
        if event_type != "ORDER_COMPLETED":
            raise PurchaseEventValidationError(
                f"Invalid eventType '{event_type}', expected 'ORDER_COMPLETED'"
            )

        order_status = payload.get("orderStatus")
        if order_status != "COMPLETED":
            raise PurchaseEventValidationError(
                f"Invalid orderStatus '{order_status}', expected 'COMPLETED'"
            )

        payment_status = payload.get("paymentStatus")
        if payment_status != "PAID":
            raise PurchaseEventValidationError(
                f"Invalid paymentStatus '{payment_status}', expected 'PAID'"
            )

        order_id = payload.get("orderId")
        if not order_id or not str(order_id).strip():
            raise PurchaseEventValidationError("Missing or empty required field 'orderId'")

        customer_id_raw = payload.get("customerId")
        customer_id = str(customer_id_raw).strip() if customer_id_raw and str(customer_id_raw).strip() else None

        event_time = self._parse_occurred_at(payload.get("occurredAt"))
        if event_time.tzinfo is None:
            raise PurchaseEventValidationError("occurredAt must include a timezone")

        raw_items = payload.get("items")
        if not isinstance(raw_items, list) or len(raw_items) == 0:
            raise PurchaseEventValidationError("Missing or empty 'items' array in order.completed event")

        canonical_interactions: list[CanonicalInteraction] = []

        for idx, item in enumerate(raw_items):
            if not isinstance(item, dict):
                raise PurchaseEventValidationError(f"Item at index {idx} is not a valid JSON object")

            order_item_id = item.get("orderItemId")
            if not order_item_id or not str(order_item_id).strip():
                raise PurchaseEventValidationError(f"Item at index {idx} missing required 'orderItemId'")

            product_variant_id_raw = item.get("productVariantId")
            product_variant_id = (
                str(product_variant_id_raw).strip()
                if product_variant_id_raw and str(product_variant_id_raw).strip()
                else None
            )

            sku_raw = item.get("sku")
            sku = str(sku_raw).strip() if sku_raw and str(sku_raw).strip() else None

            if not product_variant_id and not sku:
                raise PurchaseEventValidationError(
                    f"Item at index {idx} (orderItemId='{order_item_id}') must provide at least one product identifier ('productVariantId' or 'sku')"
                )

            quantity_raw = item.get("quantity")
            if quantity_raw is None:
                raise PurchaseEventValidationError(f"Item at index {idx} missing required 'quantity'")
            try:
                quantity = Decimal(str(quantity_raw))
            except (InvalidOperation, ValueError) as e:
                raise PurchaseEventValidationError(f"Item at index {idx} has invalid quantity '{quantity_raw}'") from e

            if not quantity.is_finite() or quantity <= 0:
                raise PurchaseEventValidationError(f"Item at index {idx} has non-positive quantity '{quantity}'")

            unit_price_raw = item.get("unitPrice")
            unit_price: Decimal | None = None
            if unit_price_raw is not None:
                try:
                    unit_price = Decimal(str(unit_price_raw))
                except (InvalidOperation, ValueError) as e:
                    raise PurchaseEventValidationError(
                        f"Item at index {idx} has invalid unitPrice '{unit_price_raw}'"
                    ) from e

            interaction = CanonicalInteraction(
                source_event_id=str(event_id).strip(),
                order_item_id=str(order_item_id).strip(),
                customer_id=customer_id,
                product_variant_id=product_variant_id,
                sku=sku,
                event_type="PURCHASE",
                quantity=quantity,
                unit_price=unit_price,
                order_id=str(order_id).strip(),
                event_time=event_time,
                metadata={
                    "orderCode": payload.get("orderCode"),
                    "paymentMethod": payload.get("paymentMethod"),
                },
            )
            canonical_interactions.append(interaction)

        return canonical_interactions
