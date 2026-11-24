"""Single transaction for verified receipts, whole-order evidence and business effects."""
from collections import defaultdict
from datetime import timezone
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.dialects import mysql, sqlite
from sqlalchemy.orm import Session

from app.kafka.purchase_event_adapter import PurchaseEventAdapter, PurchaseEventValidationError
from app.kafka.receipt_projection import ApprovedReceipt, project_receipt, replay_projection
from app.kafka.return_event_adapter import ReturnEventAdapter, ReturnEventValidationError
from app.kafka.signed_order_event import OrderIngressError, SignedOrderEvent, require_uuid
from app.models.business_report import CompletedPurchaseOrder
from app.models.interaction_event import InteractionEvent
from app.models.order_event_inbox import OrderEventDelivery, OrderEventInbox, OrderEventStream, OrderReturnLine
from app.service.evidence_fingerprint import fingerprint
from app.service.product_resolver import ProductResolutionError
from app.service.recommendation_snapshot_service import utc_now


def _lock_order(db, order_id):
    table = OrderEventStream.__table__
    if db.get_bind().dialect.name == "mysql":
        insert = mysql.insert(table).values(order_id=order_id)
        statement = insert.on_duplicate_key_update(order_id=table.c.order_id)
    elif db.get_bind().dialect.name == "sqlite":
        statement = sqlite.insert(table).values(order_id=order_id).on_conflict_do_nothing()
    else:
        raise RuntimeError("Unsupported Order inbox database")
    db.execute(statement)


def _receipt_line(item):
    for field in ("productId", "bomVersionId"):
        if item.get(field) is not None:
            require_uuid(item[field], "INVALID_PURCHASE_LINE_IDENTITY")
    if item.get("purchaseType") not in ("PRODUCT", "CUSTOM_BUNDLE") or item.get(
        "itemType", item["purchaseType"]
    ) != item["purchaseType"]:
        raise OrderIngressError("UNSUPPORTED_PURCHASE_LINE_TYPE")
    quantity = ReturnEventAdapter._quantity(item.get("quantity"), 0)
    bom_version_id = item.get("bomVersionId")
    return {
        "order_item_id": require_uuid(item.get("orderItemId"), "INVALID_PURCHASE_LINE_IDENTITY"),
        "product_variant_id": require_uuid(item.get("productVariantId"), "INVALID_PURCHASE_LINE_IDENTITY"),
        "product_id": None,
        "purchase_type": item["purchaseType"],
        "bom_version_id": bom_version_id,
        "bom_identity_status": "SOURCE_PROVIDED" if bom_version_id else (
            "UNRESOLVED" if item["purchaseType"] == "CUSTOM_BUNDLE" else "NOT_PROVIDED"
        ),
        "quantity": format(quantity.normalize(), "f"),
    }


class OrderInboxService:
    def __init__(self, interactions, purchase_adapter=None, return_adapter=None):
        self.interactions = interactions
        self.purchase_adapter = purchase_adapter or PurchaseEventAdapter()
        self.return_adapter = return_adapter or ReturnEventAdapter()

    def stage(
        self, db: Session, verified: SignedOrderEvent, key: bytes,
    ) -> tuple[list[InteractionEvent], OrderEventInbox]:
        """Stage effects and return the receipt used for the post-commit ACK decision."""
        body = verified.payload
        order_id, event_id = body["orderId"], body["eventId"]
        _lock_order(db, order_id)

        prior = db.get(OrderEventInbox, event_id)
        record = db.get(OrderEventDelivery, (verified.topic, verified.partition, verified.offset))
        if (record is not None and (record.event_id != event_id or prior is None)) or (
            prior is not None and prior.fingerprint != verified.fingerprint
        ):
            raise OrderIngressError("ORDER_EVENT_IDENTITY_CONFLICT")
        delivery = OrderEventDelivery(topic=verified.topic, partition_id=verified.partition,
                                      record_offset=verified.offset, event_id=event_id)
        if prior is not None:
            if record is None:
                db.add(delivery)
            return [], prior

        approved = project_receipt(verified.raw, verified.headers, keys={verified.key_version: key},
                                   key_version=verified.key_version)
        row = OrderEventInbox(
            event_id=event_id, order_id=order_id, topic=verified.topic, partition_id=verified.partition,
            record_offset=verified.offset, event_type=body["eventType"], fingerprint=verified.fingerprint,
            key_id=verified.key_id, key_version=verified.key_version, receipt=approved.document,
            status="UNRESOLVED", error_code="RECEIPT_DIAGNOSTIC_ONLY", observed_at=utc_now(),
        )
        db.add(row)
        db.flush()
        db.add(delivery)
        if approved.replay_level != "POLICY_PROJECTION":
            return [], row

        sanitized = replay_projection(approved)
        if row.event_type == "ORDER_COMPLETED":
            return self._purchase(db, row, sanitized), row
        return self._return(db, row, sanitized), row

    def _purchase(self, db, row, payload):
        try:
            if payload.get("completedAt") is None:
                raise OrderIngressError("COMPLETION_TIME_MISSING")
            source_lines = payload["items"]
            lines = [_receipt_line(item) for item in source_lines]
            by_id = {line["order_item_id"]: line for line in lines}
            if not lines or len(lines) != len(by_id):
                raise OrderIngressError("EMPTY_OR_DUPLICATE_PURCHASE_LINES")
            _, _, customer_id, completed_at = self.purchase_adapter.validate_completion(payload)
            product_items = [item for item in source_lines if item["purchaseType"] == "PRODUCT"]
            interactions = (self.purchase_adapter.to_canonical_interactions(dict(payload, items=product_items))
                            if product_items else [])
        except (KeyError, TypeError, AttributeError, OrderIngressError, PurchaseEventValidationError,
                ReturnEventValidationError):
            row.error_code = "INVALID_PURCHASE_EVIDENCE"
            return []

        if db.get(CompletedPurchaseOrder, row.order_id, with_for_update=True) is not None:
            row.error_code = "EXISTING_PURCHASE_REVIEW_REQUIRED"
            return []

        events = []
        projection_incomplete = False
        for interaction in interactions:
            try:
                event, = self.interactions.stage_interactions(db, [interaction])
            except ProductResolutionError:
                projection_incomplete = True
                continue
            by_id[event.order_item_id]["product_id"] = event.product_id
            events.append(event)
        occurred_at = completed_at.astimezone(timezone.utc).replace(tzinfo=None)
        lines.sort(key=lambda line: line["order_item_id"])
        db.add(CompletedPurchaseOrder(
            id=row.order_id, source_event_id=row.event_id, customer_id=customer_id,
            occurred_at=occurred_at, observed_at=row.observed_at, lines=lines,
            evidence_hash=fingerprint(dict(customer_id=customer_id, occurred_at=occurred_at.isoformat(), lines=lines)),
        ))
        row.status = "UNRESOLVED" if projection_incomplete else "APPLIED"
        row.error_code = "PRODUCT_IDENTITY_UNRESOLVED" if projection_incomplete else None
        db.flush()
        waiting = db.scalars(select(OrderEventInbox).where(
            OrderEventInbox.order_id == row.order_id, OrderEventInbox.status == "PENDING_PURCHASE",
        ).order_by(OrderEventInbox.observed_at, OrderEventInbox.event_id).with_for_update()).all()
        for pending in waiting:
            events.extend(self._return(db, pending, replay_projection(ApprovedReceipt(pending.receipt))))
            db.flush()
        return events

    def _return(self, db, row, payload):
        if "customerId" not in payload:
            row.status, row.error_code = "UNRESOLVED", "RETURN_CUSTOMER_IDENTITY_MISSING"
            return []
        try:
            preferred = self.return_adapter.to_canonical_interactions(payload)
        except ReturnEventValidationError:
            row.status, row.error_code = "UNRESOLVED", "INVALID_RETURN_EVIDENCE"
            return []
        purchase = db.get(CompletedPurchaseOrder, row.order_id, with_for_update=True)
        if purchase is None:
            row.status, row.error_code = "PENDING_PURCHASE", "PURCHASE_NOT_OBSERVED"
            return []
        if purchase.customer_id != payload.get("customerId"):
            row.status, row.error_code = "UNRESOLVED", "RETURN_CUSTOMER_MISMATCH"
            return []
        occurred_at = self.return_adapter._parse_occurred_at(payload["occurredAt"]).astimezone(
            timezone.utc
        ).replace(tzinfo=None)
        if occurred_at < purchase.occurred_at:
            row.status, row.error_code = "UNRESOLVED", "RETURN_TIME_PRECEDES_PURCHASE"
            return []

        original = {line["order_item_id"]: line for line in purchase.lines}
        applied = {line.return_item_id: line for line in db.scalars(select(OrderReturnLine).where(
            OrderReturnLine.order_id == row.order_id
        ).with_for_update())}
        legacy = db.scalars(select(InteractionEvent).where(
            InteractionEvent.order_id == row.order_id, InteractionEvent.event_type == "RETURN"
        ).with_for_update())
        for line in legacy:
            if line.return_item_id and line.return_item_id not in applied:
                applied[line.return_item_id] = line
        quantities = defaultdict(Decimal)
        for line in applied.values():
            quantities[line.order_item_id] += line.quantity
        return_id = payload["returnId"]
        existing_items = {item_id for item_id, line in applied.items() if line.return_id == return_id}
        if existing_items and existing_items != {line["returnItemId"] for line in payload["items"]}:
            row.status, row.error_code = "UNRESOLVED", "RETURN_AGGREGATE_CONFLICT"
            return []

        new_lines = []
        for line in payload["items"]:
            returned = applied.get(line["returnItemId"])
            original_line = original.get(line["orderItemId"])
            quantity = self.return_adapter._quantity(line["quantity"], 0)
            variant = line.get("productVariantId")
            reason = line["returnReason"].strip().upper()
            if original_line is None or variant != original_line["product_variant_id"]:
                row.status, row.error_code = "UNRESOLVED", "RETURN_LINE_IDENTITY_MISMATCH"
                return []
            if returned is not None:
                prior_reason = returned.reason if isinstance(returned, OrderReturnLine) else returned.return_reason
                if (returned.return_id, returned.order_item_id, returned.product_variant_id,
                    returned.customer_id, returned.quantity, prior_reason) != (
                    return_id, line["orderItemId"], variant, purchase.customer_id, quantity, reason
                ):
                    row.status, row.error_code = "UNRESOLVED", "RETURN_ITEM_CONFLICT"
                    return []
                continue
            if quantities[line["orderItemId"]] + quantity > Decimal(original_line["quantity"]):
                row.status, row.error_code = "UNRESOLVED", "RETURN_QUANTITY_EXCEEDS_PURCHASE"
                return []
            new_lines.append(OrderReturnLine(
                return_item_id=line["returnItemId"], event_id=row.event_id, return_id=return_id,
                order_id=row.order_id, order_item_id=line["orderItemId"], customer_id=purchase.customer_id,
                product_variant_id=variant, purchase_type=original_line.get("purchase_type", "PRODUCT"),
                quantity=quantity, reason=reason, observed_at=utc_now(),
                occurred_at=occurred_at,
            ))

        db.add_all(new_lines)
        new_product_returns = {line.return_item_id for line in new_lines if line.purchase_type == "PRODUCT"
                               and original[line.order_item_id]["product_id"]}
        events = []
        unresolved = False
        for interaction in preferred:
            if interaction.return_item_id in new_product_returns:
                interaction = interaction.model_copy(update={
                    "sku": None, "product_id": original[interaction.order_item_id]["product_id"],
                })
                try:
                    events.extend(self.interactions.stage_interactions(db, [interaction]))
                except ProductResolutionError:
                    unresolved = True
        row.status = "UNRESOLVED" if unresolved else "APPLIED"
        row.error_code = "RETURN_PRODUCT_UNRESOLVED" if unresolved else None
        return events
