import logging
from datetime import datetime, timezone
from decimal import Decimal
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models.customer_interaction import CustomerInteraction
from app.models.interaction_event import InteractionEventType

logger = logging.getLogger(__name__)


def is_customer_pk_conflict(exc: IntegrityError) -> bool:
    """
    Checks if an IntegrityError was specifically caused by a duplicate entry on
    customer_interactions composite primary key (customer_id, product_id).
    Ensures unexpected integrity errors (e.g. FK violations, NULL constraints) are never swallowed.
    """
    msg = str(exc).lower()
    orig = getattr(exc, "orig", None)

    # Check MySQL error code 1062 (ER_DUP_ENTRY)
    if orig and hasattr(orig, "args") and len(orig.args) > 0 and orig.args[0] == 1062:
        if "customer_interactions" in msg or "primary" in msg:
            return True

    # Check error message strings for MySQL / SQLite
    if "duplicate entry" in msg and ("primary" in msg or "customer_interactions" in msg):
        return True
    if "unique constraint failed" in msg and "customer_interactions" in msg:
        return True

    return False


class CustomerInteractionRepository:
    def find_by_customer_and_product(
        self,
        db: Session,
        customer_id: str,
        product_id: str,
    ) -> CustomerInteraction | None:
        return db.get(CustomerInteraction, (customer_id, product_id))

    def find_by_customer(
        self,
        db: Session,
        customer_id: str,
    ) -> list[CustomerInteraction]:
        stmt = (
            select(CustomerInteraction)
            .where(CustomerInteraction.customer_id == customer_id)
            .order_by(CustomerInteraction.last_interaction_time.desc())
        )
        return list(db.scalars(stmt).all())

    def apply_interaction(
        self,
        db: Session,
        customer_id: str,
        product_id: str,
        event_type: str,
        quantity: Decimal,
        event_time: datetime,
    ) -> CustomerInteraction:
        # Normalize event_time to UTC naive datetime to match MySQL DATETIME(fsp=6)
        if event_time.tzinfo is not None:
            normalized_event_time = event_time.astimezone(timezone.utc).replace(tzinfo=None)
        else:
            normalized_event_time = event_time

        is_purchase = (event_type == InteractionEventType.PURCHASE.value or event_type == InteractionEventType.PURCHASE)
        is_return = (event_type == InteractionEventType.RETURN.value or event_type == InteractionEventType.RETURN)

        # Step 1: Snapshot read to check if row exists WITHOUT acquiring gap locks.
        # In MySQL InnoDB under REPEATABLE READ, executing SELECT ... FOR UPDATE on a
        # non-existent row acquires a GAP lock. When concurrent transactions both acquire
        # gap locks and subsequently attempt INSERT, an insert intention deadlock ensues.
        # Querying without FOR UPDATE first ensures no gap lock is held when the row is absent.
        record = (
            db.query(CustomerInteraction)
            .filter(
                CustomerInteraction.customer_id == customer_id,
                CustomerInteraction.product_id == product_id,
            )
            .first()
        )

        if record is not None:
            # Row exists: acquire row-level lock safely (record lock, not gap lock)
            # populate_existing() ensures SQLAlchemy refreshes attributes from the locked row
            record = (
                db.query(CustomerInteraction)
                .filter(
                    CustomerInteraction.customer_id == customer_id,
                    CustomerInteraction.product_id == product_id,
                )
                .with_for_update()
                .populate_existing()
                .first()
            )
        else:
            new_record = CustomerInteraction(
                customer_id=customer_id,
                product_id=product_id,
                total_purchase_count=1 if is_purchase else 0,
                total_purchase_quantity=quantity if is_purchase else Decimal("0.000"),
                total_return_count=1 if is_return else 0,
                total_return_quantity=quantity if is_return else Decimal("0.000"),
                last_interaction_time=normalized_event_time,
            )
            try:
                with db.begin_nested():
                    db.add(new_record)
                    db.flush()
                return new_record
            except IntegrityError as exc:
                if not is_customer_pk_conflict(exc):
                    logger.error(
                        "Unexpected IntegrityError during customer_interactions insertion for customer_id=%s, product_id=%s: %s",
                        customer_id,
                        product_id,
                        exc,
                    )
                    raise

                logger.info(
                    "Caught concurrent customer_interactions PK race for customer_id=%s, product_id=%s. "
                    "Savepoint rolled back, locking existing row for update.",
                    customer_id,
                    product_id,
                )
                record = (
                    db.query(CustomerInteraction)
                    .filter(
                        CustomerInteraction.customer_id == customer_id,
                        CustomerInteraction.product_id == product_id,
                    )
                    .with_for_update()
                    .populate_existing()
                    .first()
                )
                if record is None:
                    raise

        # Apply aggregate increment to existing or recovered row
        if is_purchase:
            record.total_purchase_count += 1
            record.total_purchase_quantity += quantity
        elif is_return:
            record.total_return_count += 1
            record.total_return_quantity += quantity

        last_time = record.last_interaction_time
        if last_time.tzinfo is not None:
            last_time = last_time.astimezone(timezone.utc).replace(tzinfo=None)

        if normalized_event_time > last_time:
            record.last_interaction_time = normalized_event_time

        return record
