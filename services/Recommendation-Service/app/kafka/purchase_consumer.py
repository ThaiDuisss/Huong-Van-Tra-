import logging
from typing import Any

from confluent_kafka import Consumer, KafkaError
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import Settings, get_settings
from app.database.session import SessionLocal
from app.kafka.offset_commit import commit_message_offset
from app.kafka.payload_decoder import decode_kafka_payload
from app.kafka.purchase_event_adapter import PurchaseEventAdapter
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


def create_kafka_consumer(settings: Settings | None = None) -> Consumer:
    """
    Factory creating a confluent-kafka Consumer with manual offset commit.
    """
    cfg = settings or get_settings()
    conf = {
        "bootstrap.servers": cfg.kafka_bootstrap_servers,
        "group.id": cfg.kafka_group_id,
        "auto.offset.reset": cfg.kafka_auto_offset_reset,
        "enable.auto.commit": cfg.kafka_enable_auto_commit,  # False
        "enable.auto.offset.store": False,
    }
    return Consumer(conf)


class PurchaseKafkaConsumer:
    """
    Purchase Kafka Consumer processing core.
    Orchestrates:
        Kafka message -> PurchaseEventAdapter -> Scoped DB Session -> InteractionService.process_batch() -> Kafka offset commit
    """

    def __init__(
        self,
        consumer: Consumer | None = None,
        interaction_service: InteractionService | None = None,
        adapter: PurchaseEventAdapter | None = None,
        session_factory: sessionmaker[Session] = SessionLocal,
        settings: Settings | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.consumer = consumer
        self.adapter = adapter or PurchaseEventAdapter()
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
        Processes a single Kafka message representing an 'order.completed' event.
        - Enforces 1 scoped DB Session per message.
        - Commits Kafka offset ONLY after DB commit has succeeded.
        - If processing fails, rolls back DB session and does NOT commit offset.
        """
        # 1. Check Kafka-level error on message if present
        if hasattr(msg, "error") and callable(msg.error):
            err = msg.error()
            if err is not None:
                if err.code() == KafkaError._PARTITION_EOF:
                    logger.debug("Reached end of partition for message: %s", msg)
                    return []
                logger.error("Kafka error received in message: %s", err)
                raise RuntimeError(f"Kafka message error: {err}")

        # 2. Extract and decode message payload
        if hasattr(msg, "value") and callable(msg.value):
            raw_value = msg.value()
        else:
            raw_value = msg

        payload = decode_kafka_payload(raw_value)

        # 3. Normalize into CanonicalInteractions
        interactions = self.adapter.to_canonical_interactions(payload)

        # 4. Process all interactions in a single scoped DB transaction
        db: Session = self.session_factory()
        try:
            events = self.interaction_service.process_batch(db, interactions, completed_order=payload)
        except Exception as exc:
            db.rollback()
            logger.error(
                "Failed processing purchase batch for eventId=%s, orderId=%s: %s",
                payload.get("eventId"),
                payload.get("orderId"),
                exc,
            )
            raise
        finally:
            db.close()

        # 5. Commit Kafka offset ONLY after DB transaction commit succeeds
        if self.consumer is not None:
            try:
                commit_message_offset(self.consumer, msg)
                logger.info(
                    "Committed Kafka offset for eventId=%s, orderId=%s",
                    payload.get("eventId"),
                    payload.get("orderId"),
                )
            except Exception as commit_exc:
                logger.critical(
                    "DB commit succeeded for eventId=%s, orderId=%s, but Kafka offset commit failed: %s. "
                    "Message may be redelivered by Kafka and handled idempotently.",
                    payload.get("eventId"),
                    payload.get("orderId"),
                    commit_exc,
                    exc_info=True,
                )
                raise commit_exc

        return events
