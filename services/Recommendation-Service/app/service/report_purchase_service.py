from sqlalchemy import select
from decimal import Decimal
from datetime import timezone
from app.kafka.purchase_event_adapter import PurchaseEventAdapter

from app.models.business_report import CompletedPurchaseOrder
from app.service.recommendation_snapshot_service import fingerprint, utc_now


def record_completed_order(db, payload, events):
    """Called only with a validated whole Kafka purchase batch, in its transaction."""
    if (payload.get("eventType"), payload.get("orderStatus"), payload.get("paymentStatus")) != (
            "ORDER_COMPLETED", "COMPLETED", "PAID"):
        raise ValueError("Only validated completed paid orders are reportable")
    if not events or len(events) != len(payload.get("items", [])):
        raise ValueError("Incomplete order evidence")
    lines = sorted([dict(order_item_id=e.order_item_id, product_id=e.product_id,
                         product_variant_id=e.product_variant_id, quantity=format(Decimal(e.quantity).normalize(), "f"))
                    for e in events], key=lambda line: line["order_item_id"])
    if len({line["order_item_id"] for line in lines}) != len(lines):
        raise ValueError("Duplicate order lines in completed purchase")
    first = events[0]
    occurred_at = PurchaseEventAdapter._parse_occurred_at(payload.get("occurredAt"))
    if occurred_at.tzinfo is None:
        raise ValueError("Completed purchase timestamp must include timezone")
    occurred_at = occurred_at.astimezone(timezone.utc).replace(tzinfo=None)
    payload_customer = str(payload.get("customerId") or "").strip() or None
    if (first.order_id, first.customer_id, first.event_time) != (
            str(payload.get("orderId", "")).strip(), payload_customer, occurred_at):
        raise ValueError("Purchase payload differs from durable interaction evidence")
    for event, line in zip(events, payload["items"]):
        if (event.source_event_id, event.order_item_id, Decimal(event.quantity)) != (
                str(payload.get("eventId", "")).strip(), str(line.get("orderItemId", "")).strip(), Decimal(str(line["quantity"]))):
            raise ValueError("Redelivered purchase line conflicts with original evidence")
    if any((e.order_id, e.customer_id, e.event_time, e.event_type) !=
           (first.order_id, first.customer_id, first.event_time, "PURCHASE") for e in events):
        raise ValueError("Inconsistent whole order evidence")
    evidence = dict(customer_id=first.customer_id, occurred_at=first.event_time.isoformat(), lines=lines)
    digest = fingerprint(evidence)
    existing = db.scalar(select(CompletedPurchaseOrder).where(CompletedPurchaseOrder.id == first.order_id))
    if existing:
        if existing.evidence_hash != digest:
            raise ValueError("Completed order evidence conflicts with previous delivery")
        return
    db.add(CompletedPurchaseOrder(id=first.order_id, source_event_id=first.source_event_id,
        customer_id=first.customer_id, occurred_at=first.event_time, observed_at=utc_now(),
        evidence_hash=digest, lines=lines))
