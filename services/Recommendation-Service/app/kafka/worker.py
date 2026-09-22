import logging
import signal
import sys
import time
from typing import Any

from confluent_kafka import Consumer, KafkaError

from app.core.config import Settings, get_settings
from app.kafka.purchase_consumer import (
    PurchaseKafkaConsumer,
    create_kafka_consumer,
)
from app.kafka.return_consumer import ReturnKafkaConsumer

logger = logging.getLogger(__name__)


class KafkaWorker:
    """
    Standalone Kafka Worker Runtime for Recommendation-Service.
    Subscribes to 'order.completed' and 'notification.return.completed',
    polls messages in a controlled loop, dispatches processing to
    PurchaseKafkaConsumer or ReturnKafkaConsumer based on topic,
    and supports graceful shutdown.
    """

    def __init__(
        self,
        consumer: Consumer | None = None,
        purchase_consumer: PurchaseKafkaConsumer | None = None,
        return_consumer: ReturnKafkaConsumer | None = None,
        settings: Settings | None = None,
        poll_timeout: float = 1.0,
        error_backoff: float = 1.0,
    ) -> None:
        self.settings = settings or get_settings()
        self.poll_timeout = poll_timeout
        self.error_backoff = error_backoff
        self.running = False

        self.consumer = consumer or create_kafka_consumer(self.settings)
        self.purchase_consumer = purchase_consumer or PurchaseKafkaConsumer(
            consumer=self.consumer,
            settings=self.settings,
        )
        self.return_consumer = return_consumer or ReturnKafkaConsumer(
            consumer=self.consumer,
            settings=self.settings,
        )

    def _signal_handler(self, signum: int, frame: Any) -> None:
        signame = (
            signal.Signals(signum).name
            if signum in signal.Signals.__members__.values()
            else str(signum)
        )
        logger.info("Shutdown signal received (%s), stopping poll loop...", signame)
        self.running = False

    def setup_signals(self) -> None:
        try:
            signal.signal(signal.SIGINT, self._signal_handler)
            signal.signal(signal.SIGTERM, self._signal_handler)
        except (ValueError, AttributeError) as exc:
            logger.debug(
                "Could not register signal handlers (likely running outside main thread): %s",
                exc,
            )

    def run(self) -> None:
        topics = [
            self.settings.kafka_order_completed_topic,
            self.settings.kafka_return_completed_topic,
        ]
        logger.info(
            "Recommendation Kafka worker starting. "
            "Kafka bootstrap: %s, consumer group: %s, topics: %s, auto commit = false",
            self.settings.kafka_bootstrap_servers,
            self.settings.kafka_group_id,
            topics,
        )

        self.setup_signals()
        self.running = True

        self.consumer.subscribe(topics)
        logger.info("Subscribed to topics: %s", topics)

        try:
            while self.running:
                try:
                    msg = self.consumer.poll(timeout=self.poll_timeout)
                except Exception as poll_exc:
                    logger.error(
                        "Error during Kafka consumer poll: %s",
                        poll_exc,
                        exc_info=True,
                    )
                    time.sleep(self.error_backoff)
                    continue

                if msg is None:
                    continue

                if hasattr(msg, "error") and callable(msg.error):
                    err = msg.error()
                    if err is not None:
                        if err.code() == KafkaError._PARTITION_EOF:
                            logger.debug(
                                "Reached end of partition: topic=%s, partition=%s, offset=%s",
                                msg.topic(),
                                msg.partition(),
                                msg.offset(),
                            )
                        else:
                            logger.error(
                                "Kafka consumer error received: topic=%s, partition=%s, offset=%s, error=%s",
                                msg.topic(),
                                msg.partition(),
                                msg.offset(),
                                err,
                            )
                        continue

                # Dispatch message by topic
                msg_topic = (
                    msg.topic()
                    if hasattr(msg, "topic") and callable(msg.topic)
                    else getattr(msg, "topic", None)
                )

                try:
                    if msg_topic == self.settings.kafka_order_completed_topic:
                        self.purchase_consumer.consume_message(msg)
                    elif msg_topic == self.settings.kafka_return_completed_topic:
                        self.return_consumer.consume_message(msg)
                    else:
                        logger.warning(
                            "Received message from unexpected topic '%s' (partition=%s, offset=%s). Skipping.",
                            msg_topic,
                            getattr(msg, "partition", lambda: "unknown")(),
                            getattr(msg, "offset", lambda: "unknown")(),
                        )
                except Exception as proc_exc:
                    logger.error(
                        "Processing failed for message from topic=%s, partition=%s, offset=%s: %s",
                        msg_topic,
                        getattr(msg, "partition", lambda: "unknown")(),
                        getattr(msg, "offset", lambda: "unknown")(),
                        proc_exc,
                    )
                    # Bounded backoff to prevent tight CPU spin on uncommitted poison messages
                    time.sleep(self.error_backoff)

        finally:
            logger.info("Stopping poll loop and closing Kafka consumer...")
            try:
                self.consumer.close()
            except Exception as close_exc:
                logger.error("Error while closing Kafka consumer: %s", close_exc)
            logger.info("Kafka consumer closed. Worker stopped.")


# Backward compatibility alias
PurchaseWorker = KafkaWorker


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    worker = KafkaWorker()
    worker.run()


if __name__ == "__main__":
    main()
