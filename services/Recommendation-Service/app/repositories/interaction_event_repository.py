from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.interaction_event import InteractionEvent


class InteractionEventRepository:
    def find_purchase_line(self, db: Session, order_item_id: str) -> list[InteractionEvent]:
        # Whole-order evidence provides the consumer batch's durable PK fence.
        # A duplicate retry starts a new transaction/snapshot after full rollback.
        return list(db.scalars(select(InteractionEvent).where(
            InteractionEvent.order_item_id == order_item_id,
            InteractionEvent.event_type == "PURCHASE",
        )).all())

    def save(
        self,
        db: Session,
        event: InteractionEvent,
    ) -> InteractionEvent:
        db.add(event)
        return event

    def find_by_id(
        self,
        db: Session,
        event_id: str,
    ) -> InteractionEvent | None:
        return db.get(InteractionEvent, event_id)

    def find_by_source_line(
        self,
        db: Session,
        source_event_id: str,
        order_item_id: str,
        event_type: str,
    ) -> InteractionEvent | None:
        stmt = select(InteractionEvent).where(
            InteractionEvent.source_event_id == source_event_id,
            InteractionEvent.order_item_id == order_item_id,
            InteractionEvent.event_type == event_type,
        )
        return db.scalars(stmt).first()

    def find_by_customer_id(
        self,
        db: Session,
        customer_id: str,
        limit: int = 100,
    ) -> list[InteractionEvent]:
        stmt = (
            select(InteractionEvent)
            .where(InteractionEvent.customer_id == customer_id)
            .order_by(InteractionEvent.event_time.desc())
            .limit(limit)
        )
        return list(db.scalars(stmt).all())
