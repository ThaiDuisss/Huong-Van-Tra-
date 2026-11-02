from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import logging
from typing import Any

from app.schemas.interaction import CanonicalInteraction

logger = logging.getLogger(__name__)


class PurchaseEventValidationError(ValueError):
    """Raised when an order.completed Kafka payload fails schema or business state validation."""


class PurchaseEventAdapter:
    """
    Normalizes decoded 'order.completed' Kafka events into CanonicalInteraction models.
    Decoupled from Kafka consumers and database sessions.
    """

    @staticmethod
    def validate_headers(payload: dict[str, Any], headers: list | None) -> None:
        """Check EventRouter metadata when supplied; legacy plain fixtures remain readable."""
        expected = {"event_id": str(payload.get("eventId", "")),
                    "event_type": "ORDER_COMPLETED", "source_service": "order-service",
                    "event_version": str(payload.get("eventVersion", 1))}
        for key, value in headers or []:
            if key not in expected:
                continue
            try:
                decoded = value.decode("utf-8") if isinstance(value, bytes) else str(value)
            except UnicodeDecodeError as exc:
                raise PurchaseEventValidationError("Invalid contract header encoding") from exc
            if decoded != expected[key] or (key == "event_version" and decoded != "1"):
                raise PurchaseEventValidationError(f"Contract header/body mismatch: {key}")

    @staticmethod
    def _parse_occurred_at(val: Any) -> datetime:
        if not val:
            raise PurchaseEventValidationError("Missing required field 'occurredAt'")
        if isinstance(val, datetime):
            return val
        if isinstance(val, (int, float)):
            try:
                if isinstance(val, bool):
                    raise ValueError("Boolean timestamp")
                if val > 1e11:
                    return datetime.fromtimestamp(val / 1000.0, tz=timezone.utc)
                return datetime.fromtimestamp(val, tz=timezone.utc)
            except (ValueError, OverflowError, OSError) as exc:
                raise PurchaseEventValidationError("Invalid numeric timestamp") from exc
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

        # Version 1 remains readable without additive envelope fields for legacy
        # producers. Explicit unsupported versions/sources must fail closed.
        version = payload.get("eventVersion", 1)
        if type(version) is not int or version != 1:
            raise PurchaseEventValidationError("Unsupported eventVersion")
        if payload.get("sourceService", "order-service") != "order-service":
            raise PurchaseEventValidationError("Unexpected sourceService")

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

        if "customerId" not in payload:
            raise PurchaseEventValidationError("Missing customer identity field; explicit null denotes a guest")
        customer_id_raw = payload.get("customerId")
        customer_id = str(customer_id_raw).strip() if customer_id_raw and str(customer_id_raw).strip() else None
        if customer_id_raw is not None and customer_id is None:
            raise PurchaseEventValidationError("Blank customer identity is not an explicit guest")

        event_time = self._parse_occurred_at(payload.get("occurredAt"))
        if event_time.tzinfo is None:
            raise PurchaseEventValidationError("occurredAt must include a timezone")
        if "completedAt" in payload:
            completed_at = self._parse_occurred_at(payload.get("completedAt"))
            if completed_at.tzinfo is None or completed_at > event_time:
                raise PurchaseEventValidationError("Invalid completedAt timezone or completion after emission")
            event_time = completed_at

        raw_items = payload.get("items")
        if not isinstance(raw_items, list) or len(raw_items) == 0:
            raise PurchaseEventValidationError("Missing or empty 'items' array in order.completed event")

        canonical_interactions: list[CanonicalInteraction] = []
        seen_lines: set[str] = set()

        for idx, item in enumerate(raw_items):
            if not isinstance(item, dict):
                raise PurchaseEventValidationError(f"Item at index {idx} is not a valid JSON object")

            order_item_id = item.get("orderItemId")
            if not order_item_id or not str(order_item_id).strip():
                raise PurchaseEventValidationError(f"Item at index {idx} missing required 'orderItemId'")
            line_key = str(order_item_id).strip()
            if line_key in seen_lines:
                raise PurchaseEventValidationError("Duplicate orderItemId in completion")
            seen_lines.add(line_key)
            if any(item.get(key, "PRODUCT") != "PRODUCT" for key in ("purchaseType", "itemType")):
                raise PurchaseEventValidationError("Non-PRODUCT line cannot enter Product V1")

            product_variant_id_raw = item.get("productVariantId")
            product_variant_id = (
                str(product_variant_id_raw).strip()
                if product_variant_id_raw and str(product_variant_id_raw).strip()
                else None
            )

            sku_raw = item.get("sku")
            sku = str(sku_raw).strip() if sku_raw and str(sku_raw).strip() else None

            if not product_variant_id:
                raise PurchaseEventValidationError(
                    f"Item at index {idx} missing exact productVariantId"
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
            if quantity.adjusted() >= 11 or quantity != quantity.quantize(Decimal("0.001")):
                raise PurchaseEventValidationError("quantity exceeds the persisted DECIMAL(14,3) contract")

            unit_price_raw = item.get("unitPrice")
            unit_price: Decimal | None = None
            if unit_price_raw is not None:
                try:
                    unit_price = Decimal(str(unit_price_raw))
                except (InvalidOperation, ValueError) as e:
                    raise PurchaseEventValidationError(
                        f"Item at index {idx} has invalid unitPrice '{unit_price_raw}'"
                    ) from e
                if not unit_price.is_finite() or unit_price < 0:
                    raise PurchaseEventValidationError("unitPrice must be finite and nonnegative")
                if unit_price.adjusted() >= 12 or unit_price != unit_price.quantize(Decimal("0.01")):
                    raise PurchaseEventValidationError("unitPrice exceeds the persisted DECIMAL(14,2) contract")

            interaction = CanonicalInteraction(
                source_event_id=str(event_id).strip(),
                order_item_id=str(order_item_id).strip(),
                customer_id=customer_id,
                product_variant_id=product_variant_id,
                product_id=str(item["productId"]).strip() if item.get("productId") else None,
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
