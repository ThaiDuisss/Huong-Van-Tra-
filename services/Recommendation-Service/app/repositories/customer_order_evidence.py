from dataclasses import dataclass
from datetime import datetime

from sqlalchemy import and_, select
from sqlalchemy.engine import Row
from sqlalchemy.orm import Session

from app.models.business_report import CompletedPurchaseOrder
from app.models.order_event_inbox import OrderEventInbox, OrderReturnLine


@dataclass(frozen=True, eq=False)
class CustomerOrderEvidence:
    orders: tuple[Row, ...]
    returns: tuple[Row, ...]


def load_order_evidence(db: Session, customer_id: str, cutoff: datetime) -> CustomerOrderEvidence:
    """Share raw rows while preserving each mode's receipt and return eligibility policy."""
    order, returned, inbox = CompletedPurchaseOrder, OrderReturnLine, OrderEventInbox
    orders = db.execute(select(
        order.id, order.occurred_at, order.evidence_hash, order.lines, order.customer_id,
        (inbox.status == "APPLIED").label("signed_applied"),
    ).outerjoin(inbox, and_(
        inbox.event_id == order.source_event_id, inbox.order_id == order.id,
        inbox.event_type == "ORDER_COMPLETED", inbox.key_id.is_not(None),
    )).where(order.customer_id == customer_id, order.occurred_at <= cutoff, order.observed_at <= cutoff))
    returns = db.execute(select(
        returned.return_item_id, returned.order_id, returned.order_item_id,
        returned.product_variant_id, returned.quantity, returned.reason, returned.purchase_type,
        (inbox.status == "APPLIED").label("signed_applied"),
    ).outerjoin(inbox, and_(
        inbox.event_id == returned.event_id, inbox.order_id == returned.order_id,
        inbox.event_type == "RETURN_COMPLETED", inbox.key_id.is_not(None),
    )).where(returned.customer_id == customer_id, returned.occurred_at <= cutoff, returned.observed_at <= cutoff))
    return CustomerOrderEvidence(tuple(orders), tuple(returns))
