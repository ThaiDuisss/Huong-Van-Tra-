import json
import logging
from typing import Any

from confluent_kafka import Consumer, KafkaError
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import Settings, get_settings
from app.database.session import SessionLocal
from app.kafka.return_event_adapter import (
    ReturnEventAdapter,
    ReturnEventValidationError,
)
from app.models.interaction_event import InteractionEvent
from app.repositories.customer_interaction_repository import (
    CustomerInteractionRepository,
)
from app.repositories.interaction_event_repository import (
    InteractionEventRepository,
)
from app.repositories.product_variant_repository import (
    ProductVariantRepository,
)
from app.service.interaction_service import InteractionService
from app.service.product_resolver import ProductResolver

logger = logging.getLogger(__name__)


class ReturnKafkaConsumer:
    """
    Return Kafka Consumer processing core.
    Orchestrates:
        Kafka message -> ReturnEventAdapter -> Scoped DB Session -> InteractionService.process_batch() -> Kafka offset commit
    Handles operational returns gracefully as successful business no-ops with offset commit.
    """

    def __init__(
        self,
        consumer: Consumer | None = None,
        interaction_service: InteractionService | None = None,
        adapter: ReturnEventAdapter | None = None,
        session_factory: sessionmaker[Session] = SessionLocal,
        settings: Settings | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.consumer = consumer
        self.adapter = adapter or ReturnEventAdapter()
        self.session_factory = session_factory

        if interaction_service is not None:
            self.interaction_service = interaction_service
        else:
            variant_repo = ProductVariantRepository()
            product_resolver = ProductResolver(variant_repo)
            event_repo = InteractionEventRepository()
            customer_repo = CustomerInteractionRepository()
            self.interaction_service = InteractionService(
                product_resolver=product_resolver,
                interaction_event_repository=event_repo,
                customer_interaction_repository=customer_repo,
            )

    def consume_message(self, msg: Any) -> list[InteractionEvent]:
        """
        Processes a single Kafka message representing a 'notification.return.completed' event.
        - Enforces 1 scoped DB Session per message.
        - If the event contains only operational items (no-op), commits Kafka offset without DB transaction.
        - If preference-negative items exist, commits DB transaction first, then commits Kafka offset.
        - If processing fails, rolls back DB session and does NOT commit offset.
        """
        # 1. Check Kafka-level error on message if present
        if hasattr(msg, "error") and callable(msg.error):
            err = msg.error()
            if err is not None:
                if err.code() == KafkaError._PARTITION_EOF:
                    logger.debug("Reached end of partition for return message: %s", msg)
                    return []
                logger.error("Kafka error received in return message: %s", err)
                raise RuntimeError(f"Kafka message error: {err}")

        # 2. Extract and decode message payload
        if hasattr(msg, "value") and callable(msg.value):
            raw_value = msg.value()
        else:
            raw_value = msg

        if raw_value is None:
            raise ReturnEventValidationError("Kafka message value is None")

        if isinstance(raw_value, (bytes, bytearray)):
            payload_str = raw_value.decode("utf-8")
            payload = json.loads(payload_str)
        elif isinstance(raw_value, str):
            payload = json.loads(raw_value)
        elif isinstance(raw_value, dict):
            payload = raw_value
        else:
            raise ReturnEventValidationError(f"Unsupported message value type: {type(raw_value)}")

        # 3. Normalize into CanonicalInteractions (filters out operational returns)
        interactions = self.adapter.to_canonical_interactions(payload)

        # 4. Handle business no-op: all items operational / non-preference
        if not interactions:
            logger.info(
                "Return eventId=%s (returnId=%s) contains only operational/non-preference items. "
                "Skipping DB write and committing offset as successful business no-op.",
                payload.get("eventId"),
                payload.get("returnId"),
            )
            if self.consumer is not None:
                self.consumer.commit(message=msg, asynchronous=False)
            return []

        # 5. Process preference-negative interactions in a single scoped DB transaction
        db: Session = self.session_factory()
        try:
            events = self.interaction_service.process_batch(db, interactions)
        except Exception as exc:
            db.rollback()
            logger.error(
                "Failed processing return batch for eventId=%s, returnId=%s: %s",
                payload.get("eventId"),
                payload.get("returnId"),
                exc,
            )
            raise
        finally:
            db.close()

        # 6. Commit Kafka offset ONLY after DB transaction commit succeeds
        if self.consumer is not None:
            try:
                self.consumer.commit(message=msg, asynchronous=False)
                logger.info(
                    "Committed Kafka offset for return eventId=%s, returnId=%s",
                    payload.get("eventId"),
                    payload.get("returnId"),
                )
            except Exception as commit_exc:
                logger.critical(
                    "DB commit succeeded for return eventId=%s, returnId=%s, but Kafka offset commit failed: %s. "
                    "Message may be redelivered by Kafka and handled idempotently.",
                    payload.get("eventId"),
                    payload.get("returnId"),
                    commit_exc,
                    exc_info=True,
                )
                raise commit_exc

        return events
