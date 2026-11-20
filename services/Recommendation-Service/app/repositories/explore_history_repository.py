from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal, InvalidOperation

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.business_report import CompletedPurchaseOrder
from app.models.interaction_event import InteractionEvent, InteractionEventType
from app.models.product_variant import ProductVariant
from app.service.evidence_fingerprint import fingerprint


class ExploreHistoryError(RuntimeError):
    """Durable purchase or return evidence does not satisfy the serving contract."""


@dataclass(frozen=True, slots=True)
class ExploreHistory:
    valid_order_count: int
    product_counts: dict[str, int]
    purchased_ids: set[str]


def _quantity(value: object) -> Decimal:
    try:
        quantity = Decimal(str(value))
    except InvalidOperation:
        raise ExploreHistoryError from None
    if not quantity.is_finite() or quantity <= 0:
        raise ExploreHistoryError
    return quantity


class ExploreHistoryRepository:
    def history(self, db: Session, customer_id: str, as_of: datetime) -> ExploreHistory:
        orders = db.execute(select(
            CompletedPurchaseOrder.id, CompletedPurchaseOrder.occurred_at,
            CompletedPurchaseOrder.evidence_hash, CompletedPurchaseOrder.lines,
        ).where(
            CompletedPurchaseOrder.customer_id == customer_id,
            CompletedPurchaseOrder.occurred_at <= as_of,
            CompletedPurchaseOrder.observed_at <= as_of,
        ))
        purchases = set(db.scalars(select(InteractionEvent.product_id).where(
            InteractionEvent.customer_id == customer_id,
            InteractionEvent.event_type == InteractionEventType.PURCHASE.value,
            InteractionEvent.event_time <= as_of,
            InteractionEvent.created_at <= as_of,
        ).distinct()))

        # Only preference returns are retained in this projection by the current consumer.
        return_order_id = InteractionEvent.order_id
        if db.get_bind().dialect.name == "mysql":
            # Match the receipt collation without wrapping its indexed primary key.
            return_order_id = return_order_id.collate("utf8mb4_0900_ai_ci")
        returns = db.execute(select(
            InteractionEvent.return_item_id, InteractionEvent.order_id, InteractionEvent.order_item_id,
            InteractionEvent.product_variant_id, InteractionEvent.quantity,
        ).join(CompletedPurchaseOrder, CompletedPurchaseOrder.id == return_order_id).where(
            InteractionEvent.customer_id == customer_id,
            CompletedPurchaseOrder.customer_id == customer_id,
            CompletedPurchaseOrder.occurred_at <= as_of,
            CompletedPurchaseOrder.observed_at <= as_of,
            InteractionEvent.event_type == InteractionEventType.RETURN.value,
            InteractionEvent.event_time <= as_of,
            InteractionEvent.created_at <= as_of,
        ))
        returned_units: dict[tuple[str, str], dict[str, Decimal]] = defaultdict(dict)
        observed_returns = {}
        for return_id, order_id, line_id, variant_id, quantity in returns:
            if not return_id or not order_id or not line_id or not variant_id:
                raise ExploreHistoryError
            identity = (order_id, line_id, variant_id)
            quantity = _quantity(quantity)
            previous = observed_returns.get(return_id)
            if previous is not None:
                if previous != (identity, quantity):
                    raise ExploreHistoryError
                continue
            observed_returns[return_id] = identity, quantity
            matching_line = returned_units[order_id, line_id]
            matching_line[variant_id] = matching_line.get(variant_id, Decimal(0)) + quantity

        strengths = Counter()
        valid_orders = 0
        for order_id, occurred_at, expected_hash, lines in orders:
            if not isinstance(lines, list) or not lines or fingerprint({
                "customer_id": customer_id, "occurred_at": occurred_at.isoformat(), "lines": lines,
            }) != expected_hash:
                raise ExploreHistoryError
            has_retained_line = False
            seen_lines = set()
            for line in lines:
                if not isinstance(line, dict):
                    raise ExploreHistoryError
                line_id, product_id, variant_id = (
                    line.get("order_item_id"), line.get("product_id"), line.get("product_variant_id"),
                )
                if not all(isinstance(value, str) and value for value in (line_id, product_id, variant_id)):
                    raise ExploreHistoryError
                if line_id in seen_lines:
                    raise ExploreHistoryError
                seen_lines.add(line_id)

                purchased_quantity = _quantity(line.get("quantity"))
                matched_returns = returned_units.get((order_id, line_id), {})
                returned = matched_returns.get(variant_id, Decimal(0))
                if set(matched_returns) - {variant_id} or returned > purchased_quantity:
                    raise ExploreHistoryError
                # A return can remove preference evidence without making a past purchase novel.
                purchases.add(product_id)
                if returned < purchased_quantity:
                    strengths[product_id] += 1
                    has_retained_line = True
            if has_retained_line:
                valid_orders += 1

        return ExploreHistory(valid_orders, dict(strengths), purchases)

    def commercial_product_ids(self, db: Session) -> set[str]:
        return set(db.scalars(select(ProductVariant.product_id).where(
            ProductVariant.status == "ACTIVE",
            ProductVariant.is_deleted.is_(False),
            ProductVariant.sku != "CUSTOM-BUNDLE",
        ).distinct()))
