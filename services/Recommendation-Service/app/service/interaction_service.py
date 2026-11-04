from datetime import timezone
import logging
import uuid

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models.interaction_event import InteractionEvent
from app.repositories.customer_interaction_repository import (
    CustomerInteractionRepository,
)
from app.repositories.interaction_event_repository import (
    InteractionEventRepository,
)
from app.schemas.interaction import CanonicalInteraction
from app.service.product_resolver import (
    ProductResolutionError,
    ProductResolver,
)

logger = logging.getLogger(__name__)


class PurchaseConflictError(ValueError):
    """A logical purchase line was redelivered with different economic content."""


def is_interaction_duplicate_conflict(exc: IntegrityError) -> bool:
    """
    Checks if an IntegrityError was specifically caused by a duplicate entry on
    interaction_events (uq_interaction_events_source_line or primary key).
    Ensures unexpected integrity errors (e.g. FK violations, NULL constraints) are never swallowed.
    """
    msg = str(exc).lower()
    orig = getattr(exc, "orig", None)
    # The whole-order evidence PK is the existing durable fence for consumer
    # batches. A loser rolls back interactions AND aggregates before retrying.
    if "report_purchase_orders" in msg and (
        "unique constraint failed" in msg or (orig and orig.args and orig.args[0] == 1062)
    ):
        return True

    # Check MySQL error code 1062 (ER_DUP_ENTRY)
    if orig and hasattr(orig, "args") and len(orig.args) > 0 and orig.args[0] == 1062:
        if "interaction_events" in msg or "uq_interaction_events_source_line" in msg:
            return True

    # Check error message strings for MySQL / SQLite
    if "uq_interaction_events_source_line" in msg:
        return True
    if "duplicate entry" in msg and "interaction_events" in msg:
        return True
    if "unique constraint failed" in msg and "interaction_events" in msg:
        return True

    return False


class InteractionService:
    """
    Interaction Core Service.
    Orchestrates:
        CanonicalInteraction -> ProductResolver -> InteractionEventRepository -> CustomerInteractionRepository
    """

    def __init__(
        self,
        product_resolver: ProductResolver,
        interaction_event_repository: InteractionEventRepository,
        customer_interaction_repository: CustomerInteractionRepository,
    ) -> None:
        self.product_resolver = product_resolver
        self.interaction_event_repository = interaction_event_repository
        self.customer_interaction_repository = customer_interaction_repository

    def _process_one(
        self,
        db: Session,
        interaction: CanonicalInteraction,
    ) -> InteractionEvent:
        """
        Mutates session state for a single canonical interaction without committing.
        Allows process() and process_batch() to define their own transaction boundaries.
        """
        # 1. Product resolution: Resolve variant and product_id using local read-model
        product_id = interaction.product_id
        product_variant_id = interaction.product_variant_id

        variant = self.product_resolver.resolve_variant(
            db,
            product_variant_id=interaction.product_variant_id,
            # Strict authority identity for PURCHASE; retain the existing
            # resolution contract for unrelated RETURN callers.
            sku=None if interaction.event_type == "PURCHASE" else interaction.sku,
        )

        if interaction.event_type == "PURCHASE":
            if variant is None or not interaction.product_variant_id or variant.id != interaction.product_variant_id:
                raise ProductResolutionError("PURCHASE requires an exact known variant")
            if product_id and product_id != variant.product_id:
                raise ProductResolutionError("Product identity contradicts variant binding")
            normalized = (interaction.event_time.astimezone(timezone.utc).replace(tzinfo=None)
                          if interaction.event_time.tzinfo else interaction.event_time)
            existing_lines = self.interaction_event_repository.find_purchase_line(db, interaction.order_item_id)
            if len(existing_lines) > 1:
                raise PurchaseConflictError("Multiple durable purchases for one business line")
            if existing_lines:
                existing = existing_lines[0]
                if (existing.order_id, existing.customer_id, existing.product_variant_id,
                    existing.product_id, existing.quantity, existing.unit_price, existing.event_time) != (
                    interaction.order_id, interaction.customer_id, variant.id,
                    variant.product_id, interaction.quantity, interaction.unit_price, normalized):
                    raise PurchaseConflictError("Conflicting purchase business line")
                return existing

        if variant is not None:
            product_id = variant.product_id
            product_variant_id = variant.id

        if not product_id or not product_variant_id:
            raise ProductResolutionError(
                f"Cannot resolve product for variant_id='{interaction.product_variant_id}', "
                f"sku='{interaction.sku}', product_id='{interaction.product_id}'"
            )

        # 2. Idempotency pre-check based on (source_event_id, order_item_id, event_type)
        existing_event = self.interaction_event_repository.find_by_source_line(
            db,
            source_event_id=interaction.source_event_id,
            order_item_id=interaction.order_item_id,
            event_type=interaction.event_type,
        )
        if existing_event is not None:
            logger.info(
                "Duplicate canonical interaction detected and skipped: "
                "source_event_id=%s, order_item_id=%s, event_type=%s",
                interaction.source_event_id,
                interaction.order_item_id,
                interaction.event_type,
            )
            return existing_event

        # 3. Normalize event_time to UTC naive datetime for MySQL DATETIME(fsp=6)
        event_time = interaction.event_time
        if event_time.tzinfo is not None:
            normalized_event_time = event_time.astimezone(timezone.utc).replace(tzinfo=None)
        else:
            normalized_event_time = event_time

        # 4. Create and persist InteractionEvent
        event = InteractionEvent(
            id=str(uuid.uuid4()),
            source_event_id=interaction.source_event_id,
            order_item_id=interaction.order_item_id,
            return_item_id=interaction.return_item_id,
            customer_id=interaction.customer_id,
            product_variant_id=product_variant_id,
            product_id=product_id,
            event_type=interaction.event_type,
            quantity=interaction.quantity,
            unit_price=interaction.unit_price,
            order_id=interaction.order_id,
            return_id=interaction.return_id,
            return_reason=interaction.return_reason,
            event_time=normalized_event_time,
        )
        self.interaction_event_repository.save(db, event)

        # 5. Customer Aggregation: ONLY if customer_id is provided and non-empty
        if interaction.customer_id is not None and interaction.customer_id.strip():
            self.customer_interaction_repository.apply_interaction(
                db,
                customer_id=interaction.customer_id.strip(),
                product_id=product_id,
                event_type=interaction.event_type,
                quantity=interaction.quantity,
                event_time=normalized_event_time,
            )

        return event

    def process(
        self,
        db: Session,
        interaction: CanonicalInteraction,
    ) -> InteractionEvent:
        """
        Processes a single CanonicalInteraction within an atomic local DB transaction boundary.
        """
        try:
            event = self._process_one(db, interaction)
            db.commit()
            db.refresh(event)
            return event
        except IntegrityError as exc:
            db.rollback()
            if is_interaction_duplicate_conflict(exc):
                existing = self.interaction_event_repository.find_by_source_line(
                    db,
                    source_event_id=interaction.source_event_id,
                    order_item_id=interaction.order_item_id,
                    event_type=interaction.event_type,
                )
                if existing is not None:
                    logger.warning(
                        "Concurrent duplicate insertion caught by unique constraint: "
                        "source_event_id=%s, order_item_id=%s, event_type=%s",
                        interaction.source_event_id,
                        interaction.order_item_id,
                        interaction.event_type,
                    )
                    return existing
            raise
        except Exception:
            db.rollback()
            raise

    def process_batch(
        self,
        db: Session,
        interactions: list[CanonicalInteraction],
        completed_order: dict | None = None,
    ) -> list[InteractionEvent]:
        """
        Processes multiple CanonicalInteractions in a single atomic local DB transaction boundary.
        If any interaction fails, the entire batch is rolled back.
        Handles concurrent duplicate races by performing a single bounded retry after full rollback
        if and only if the failure was an expected duplicate constraint conflict.
        """
        try:
            events: list[InteractionEvent] = []
            for interaction in interactions:
                event = self._process_one(db, interaction)
                events.append(event)
            if completed_order is not None:
                from app.service.report_purchase_service import record_completed_order
                record_completed_order(db, completed_order, events)
            db.commit()
            for event in events:
                db.refresh(event)
            return events
        except IntegrityError as exc:
            db.rollback()
            if not is_interaction_duplicate_conflict(exc):
                logger.error(
                    "Unexpected IntegrityError during batch commit: %s", exc
                )
                raise

            logger.warning(
                "Caught concurrent duplicate IntegrityError on batch commit (%s). "
                "Rolled back outer transaction; executing single bounded retry: %s",
                type(exc).__name__,
                exc,
            )

            # Bounded retry: exactly 1 attempt from clean DB state
            try:
                events = []
                for interaction in interactions:
                    event = self._process_one(db, interaction)
                    events.append(event)
                if completed_order is not None:
                    from app.service.report_purchase_service import record_completed_order
                    record_completed_order(db, completed_order, events)
                db.commit()
                for event in events:
                    db.refresh(event)
                return events
            except Exception:
                db.rollback()
                raise
        except Exception:
            db.rollback()
            raise
