from collections import Counter, defaultdict
from collections.abc import Iterable
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal, InvalidOperation

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.business_report import CompletedPurchaseOrder
from app.models.interaction_event import InteractionEvent, InteractionEventType
from app.models.order_event_inbox import OrderReturnLine
from app.models.product_variant import ProductVariant
from app.service.evidence_fingerprint import fingerprint


class ExploreHistoryError(RuntimeError):
    """Durable purchase or return evidence does not satisfy the serving contract."""


@dataclass(frozen=True, slots=True)
class ProductPurchaseHistory:
    retained_order_times: tuple[datetime, ...]
    net_quantity: Decimal
    purchased_lines: int
    returned_lines: int
    return_reasons: dict[str, int]
    unknown_reason_returns: int


@dataclass(frozen=True, slots=True)
class ExploreHistory:
    valid_order_count: int
    product_counts: dict[str, int]
    purchased_ids: set[str]
    last_retained_purchase: dict[str, datetime] | None = None
    product_history: dict[str, ProductPurchaseHistory] = field(default_factory=dict)


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

        # Before signed ingestion, only preference returns had a persisted history row.
        return_order_id = InteractionEvent.order_id
        if db.get_bind().dialect.name == "mysql":
            # Match the receipt collation without wrapping its indexed primary key.
            return_order_id = return_order_id.collate("utf8mb4_0900_ai_ci")
        returns = db.execute(select(
            InteractionEvent.return_item_id, InteractionEvent.order_id, InteractionEvent.order_item_id,
            InteractionEvent.product_variant_id, InteractionEvent.quantity, InteractionEvent.return_reason,
        ).join(CompletedPurchaseOrder, CompletedPurchaseOrder.id == return_order_id).where(
            InteractionEvent.customer_id == customer_id,
            CompletedPurchaseOrder.customer_id == customer_id,
            CompletedPurchaseOrder.occurred_at <= as_of,
            CompletedPurchaseOrder.observed_at <= as_of,
            InteractionEvent.event_type == InteractionEventType.RETURN.value,
            InteractionEvent.event_time <= as_of,
            InteractionEvent.created_at <= as_of,
        ))
        signed_returns = db.execute(select(
            OrderReturnLine.return_item_id, OrderReturnLine.order_id, OrderReturnLine.order_item_id,
            OrderReturnLine.product_variant_id, OrderReturnLine.quantity, OrderReturnLine.reason,
        ).where(
            OrderReturnLine.customer_id == customer_id,
            OrderReturnLine.purchase_type == "PRODUCT",
            OrderReturnLine.occurred_at <= as_of,
            OrderReturnLine.observed_at <= as_of,
        ))
        return build_history(customer_id, orders, purchases, signed_returns, returns)

    def commercial_variants(self, db: Session) -> dict[str, str]:
        return dict(db.execute(select(ProductVariant.id, ProductVariant.product_id).where(
            ProductVariant.status == "ACTIVE",
            ProductVariant.is_deleted.is_(False),
            ProductVariant.sku != "CUSTOM-BUNDLE",
        )).tuples().all())


def receipt_products(customer_id: str, occurred_at: datetime, expected_hash: str,
                     lines: list) -> Iterable[tuple[str, str, str, Decimal]]:
    if not isinstance(lines, list) or not lines or fingerprint({
        "customer_id": customer_id, "occurred_at": occurred_at.isoformat(), "lines": lines,
    }) != expected_hash:
        raise ExploreHistoryError
    seen_lines = set()
    for line in lines:
        if not isinstance(line, dict):
            raise ExploreHistoryError
        line_type = line.get("purchase_type", "PRODUCT")
        if line_type == "CUSTOM_BUNDLE" or (line_type == "PRODUCT" and
                                            "purchase_type" in line and line.get("product_id") is None):
            continue
        if line_type != "PRODUCT":
            raise ExploreHistoryError
        line_id, product_id, variant_id = (
            line.get("order_item_id"), line.get("product_id"), line.get("product_variant_id"),
        )
        if not all(isinstance(value, str) and value for value in (line_id, product_id, variant_id)):
            raise ExploreHistoryError
        if line_id in seen_lines:
            raise ExploreHistoryError
        seen_lines.add(line_id)
        yield line_id, product_id, variant_id, _quantity(line.get("quantity"))


def build_history(customer_id: str, orders: Iterable, purchases: Iterable[str],
                  signed_returns: Iterable, legacy_returns: Iterable = ()) -> ExploreHistory:
    purchased_ids = set(purchases)
    returned_units: dict[tuple[str, str], dict[str, Decimal]] = defaultdict(dict)
    line_reasons: dict[tuple[str, str], Counter] = defaultdict(Counter)
    observed_returns = {}
    for entry in (entry for result in (signed_returns, legacy_returns) for entry in result):
        return_id, order_id, line_id, variant_id, quantity, *reasons = entry
        if not return_id or not order_id or not line_id or not variant_id:
            raise ExploreHistoryError
        identity = (order_id, line_id, variant_id)
        quantity = _quantity(quantity)
        reason = reasons[0] if reasons else None
        previous = observed_returns.get(return_id)
        if previous is not None:
            if previous[:2] != (identity, quantity) or (
                previous[2] and reason and previous[2] != reason
            ):
                raise ExploreHistoryError
            if reason and not previous[2]:
                line_reasons[order_id, line_id][None] -= 1
                line_reasons[order_id, line_id][reason] += 1
                observed_returns[return_id] = identity, quantity, reason
            continue
        observed_returns[return_id] = identity, quantity, reason
        matching_line = returned_units[order_id, line_id]
        matching_line[variant_id] = matching_line.get(variant_id, Decimal(0)) + quantity
        line_reasons[order_id, line_id][reason] += 1

    strengths = Counter()
    recency = {}
    purchase_lines, returned_lines = Counter(), Counter()
    retained_orders: dict[str, list[datetime]] = defaultdict(list)
    quantities: dict[str, Decimal] = defaultdict(Decimal)
    product_reasons: dict[str, Counter] = defaultdict(Counter)
    seen_orders = {}
    valid_orders = 0
    for order_id, occurred_at, expected_hash, lines in orders:
        identity = (occurred_at, expected_hash, lines)
        if order_id in seen_orders:
            if seen_orders[order_id] != identity:
                raise ExploreHistoryError
            continue
        seen_orders[order_id] = identity
        retained_products = set()
        for line_id, product_id, variant_id, purchased_quantity in receipt_products(
            customer_id, occurred_at, expected_hash, lines
        ):
            matched_returns = returned_units.get((order_id, line_id), {})
            returned = matched_returns.get(variant_id, Decimal(0))
            if set(matched_returns) - {variant_id} or returned > purchased_quantity:
                raise ExploreHistoryError
            # A return can remove preference evidence without making a past purchase novel.
            purchased_ids.add(product_id)
            purchase_lines[product_id] += 1
            returned_lines[product_id] += int(returned > 0)
            product_reasons[product_id].update(line_reasons[order_id, line_id])
            quantities[product_id] += purchased_quantity - returned
            if returned < purchased_quantity:
                strengths[product_id] += 1
                recency[product_id] = max(occurred_at, recency.get(product_id, occurred_at))
                retained_products.add(product_id)
        for product_id in retained_products:
            retained_orders[product_id].append(occurred_at)
        if retained_products:
            valid_orders += 1

    product_history = {
        product_id: ProductPurchaseHistory(tuple(sorted(retained_orders[product_id])), quantities[product_id],
                                           count, returned_lines[product_id],
                                           {reason: count for reason, count in product_reasons[product_id].items()
                                            if reason is not None},
                                           product_reasons[product_id][None])
        for product_id, count in purchase_lines.items()
    }
    return ExploreHistory(valid_orders, dict(strengths), purchased_ids, recency, product_history)
