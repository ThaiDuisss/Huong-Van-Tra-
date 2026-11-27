from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.business_report import CompletedPurchaseOrder
from app.models.order_event_inbox import OrderEventInbox, OrderReturnLine
from app.repositories.customer_order_evidence import CustomerOrderEvidence
from app.repositories.explore_history_repository import ExploreHistoryError, _quantity, receipt_products


@dataclass(frozen=True)
class RetainedLine:
    order_item_id: UUID
    product_id: UUID | None
    variant_id: UUID
    quantity: Decimal
    order_id: str | None = None
    occurred_at: datetime | None = None
    purchased_quantity: Decimal | None = None
    components: tuple[dict, ...] = ()
    purchase_type: str = "PRODUCT"


class IngredientEvidenceLimit(ExploreHistoryError):
    pass


def retained_lines(db: Session, customer_id: str | None, cutoff: datetime, *,
                   include_bundles: bool = False, start: datetime | None = None,
                   end: datetime | None = None, max_orders: int | None = None) -> list[RetainedLine]:
    orders = select(CompletedPurchaseOrder).join(
        OrderEventInbox, OrderEventInbox.event_id == CompletedPurchaseOrder.source_event_id,
    ).where(
        CompletedPurchaseOrder.occurred_at <= cutoff, CompletedPurchaseOrder.observed_at <= cutoff,
        OrderEventInbox.order_id == CompletedPurchaseOrder.id,
        OrderEventInbox.event_type == "ORDER_COMPLETED", OrderEventInbox.status == "APPLIED",
        OrderEventInbox.key_id.is_not(None),
    )
    returns = select(
        OrderReturnLine.order_id, OrderReturnLine.order_item_id, OrderReturnLine.product_variant_id,
        OrderReturnLine.purchase_type, OrderReturnLine.quantity,
    ).join(OrderEventInbox, OrderEventInbox.event_id == OrderReturnLine.event_id).where(
        OrderReturnLine.occurred_at <= cutoff, OrderReturnLine.observed_at <= cutoff,
        OrderEventInbox.order_id == OrderReturnLine.order_id,
        OrderEventInbox.event_type == "RETURN_COMPLETED", OrderEventInbox.status == "APPLIED",
        OrderEventInbox.key_id.is_not(None),
    )
    if customer_id is not None:
        orders = orders.where(CompletedPurchaseOrder.customer_id == customer_id)
        returns = returns.where(OrderReturnLine.customer_id == customer_id)
    if start is not None:
        orders = orders.where(CompletedPurchaseOrder.occurred_at >= start, CompletedPurchaseOrder.occurred_at < end)
        returns = returns.where(OrderReturnLine.order_id.in_(orders.with_only_columns(CompletedPurchaseOrder.id)))
    if max_orders is not None:
        orders = orders.limit(max_orders + 1)
    purchases = db.scalars(orders).all()
    if max_orders is not None and len(purchases) > max_orders:
        raise IngredientEvidenceLimit("Purchase evidence exceeds report limit")
    if not include_bundles:
        returns = returns.where(OrderReturnLine.purchase_type == "PRODUCT")

    return _retain(purchases, db.execute(returns), include_bundles)


def retained_from_evidence(evidence: CustomerOrderEvidence) -> list[RetainedLine]:
    return _retain(
        (row for row in evidence.orders if row.signed_applied),
        ((row.order_id, row.order_item_id, row.product_variant_id, row.purchase_type, row.quantity)
         for row in evidence.returns if row.signed_applied), True,
    )


def _retain(purchases, returns, include_bundles: bool) -> list[RetainedLine]:
    returned: dict[tuple[str, str, str, str], Decimal] = defaultdict(Decimal)
    for order_id, line_id, variant_id, line_type, amount in returns:
        returned[(order_id, line_id, variant_id, line_type)] += _quantity(amount)

    result = []
    seen = set()

    def add(order, line_id, product_id, variant_id, amount, line_type, components=()):
        quantity = amount - returned.pop((order.id, line_id, variant_id, line_type), Decimal(0))
        if quantity < 0 or line_id in seen:
            raise ExploreHistoryError("Invalid retained purchase line")
        seen.add(line_id)
        result.append(RetainedLine(UUID(line_id), UUID(product_id) if product_id else None, UUID(variant_id),
                                   quantity, order.id, order.occurred_at, amount, components, line_type))

    for order in purchases:
        for line_id, product_id, variant_id, amount in receipt_products(
                order.customer_id, order.occurred_at, order.evidence_hash, order.lines):
            add(order, line_id, product_id, variant_id, amount, "PRODUCT")
        if include_bundles:
            for line in order.lines:
                if line.get("purchase_type") == "CUSTOM_BUNDLE":
                    add(order, line["order_item_id"], None, line["product_variant_id"], _quantity(line["quantity"]),
                        "CUSTOM_BUNDLE", tuple(line.get("components", ())))
    if returned:
        raise ExploreHistoryError("Return without a matching signed receipt")
    return sorted(result, key=lambda line: line.order_item_id)
