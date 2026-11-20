import logging
from functools import cached_property
from typing import Any

from confluent_kafka import Consumer, KafkaError
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import Settings, get_settings
from app.database.session import SessionLocal
from app.kafka.offset_commit import commit_message_offset
from app.kafka.signed_order_event import load_keyring, verify_order_event
from app.models.interaction_event import InteractionEvent
from app.repositories.customer_interaction_repository import CustomerInteractionRepository
from app.repositories.interaction_event_repository import InteractionEventRepository
from app.repositories.product_variant_repository import ProductVariantRepository
from app.service.interaction_service import InteractionService
from app.service.order_inbox_service import OrderInboxService
from app.service.product_resolver import ProductResolver

logger = logging.getLogger(__name__)


def create_kafka_consumer(settings: Settings | None = None) -> Consumer:
    cfg = settings or get_settings()
    if cfg.kafka_enable_auto_commit:
        raise ValueError("Kafka auto-commit must be disabled for transactional ingestion")
    return Consumer({
        "bootstrap.servers": cfg.kafka_bootstrap_servers,
        "group.id": cfg.kafka_group_id,
        "auto.offset.reset": cfg.kafka_auto_offset_reset,
        "enable.auto.commit": False,
        "enable.auto.offset.store": False,
    })


class OrderKafkaConsumer:
    def __init__(
        self,
        consumer: Consumer | None = None,
        interaction_service: InteractionService | None = None,
        session_factory: sessionmaker[Session] = SessionLocal,
        settings: Settings | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.consumer = consumer
        self.session_factory = session_factory
        self.inbox = OrderInboxService(interaction_service or InteractionService(
            product_resolver=ProductResolver(ProductVariantRepository()),
            interaction_event_repository=InteractionEventRepository(),
            customer_interaction_repository=CustomerInteractionRepository(),
        ))

    @cached_property
    def signing_keys(self):
        return load_keyring(self.settings.order_kafka_verification_keys.get_secret_value())

    def consume_message(self, msg: Any) -> list[InteractionEvent]:
        error = msg.error()
        if error is not None:
            if error.code() == KafkaError._PARTITION_EOF:
                return []
            raise RuntimeError("KAFKA_MESSAGE_ERROR")

        event = verify_order_event(msg, event_type=self.event_type,
                                   topic=getattr(self.settings, self.topic_setting), keys=self.signing_keys)
        key = self.signing_keys[event.key_id, event.key_version]
        with self.session_factory() as db:
            with db.begin():
                events = self.inbox.stage(db, event, key)
            for interaction in events:
                db.refresh(interaction)

        if self.consumer is not None:
            commit_message_offset(self.consumer, msg)
        return events


class PurchaseKafkaConsumer(OrderKafkaConsumer):
    event_type = "ORDER_COMPLETED"
    topic_setting = "kafka_order_completed_topic"
