package org.hvtra.iventoryservice.outbox.polling;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
final class InventoryOutboxPollingPublisher implements AutoCloseable {
    private final InventoryOutboxPublicationStore publications;
    private final InventoryOutboxRecordFactory records;
    private final KafkaOperations<String, String> kafka;
    private final InventoryOutboxPollingProperties settings;
    private final Runnable closeProducer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("inventory-outbox-poll").daemon().factory());

    InventoryOutboxPollingPublisher(InventoryOutboxPublicationStore publications,
                                    InventoryOutboxRecordFactory records,
                                    KafkaOperations<String, String> kafka,
                                    InventoryOutboxPollingProperties settings,
                                    Runnable closeProducer) {
        this.publications = publications;
        this.records = records;
        this.kafka = kafka;
        this.settings = settings;
        this.closeProducer = closeProducer;
    }

    void start() {
        scheduler.scheduleWithFixedDelay(this::pollSafely, 0, settings.intervalMs(), TimeUnit.MILLISECONDS);
    }

    private void pollSafely() {
        try {
            pollOnce();
        } catch (RuntimeException exception) {
            log.warn("Inventory Outbox polling interrupted by {}", exception.getClass().getSimpleName());
        }
    }

    void pollOnce() {
        for (String id : publications.due(settings.batchSize())) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            publications.claim(id, settings.leaseMs()).ifPresent(this::publish);
        }
    }

    private void publish(PollingInventoryEvent event) {
        ProducerRecord<String, String> record;
        try {
            record = records.create(event);
        } catch (InventoryOutboxRecordFactory.RejectedEvent exception) {
            quarantine(event, exception.getMessage());
            return;
        }

        SendResult<String, String> result;
        try {
            result = kafka.send(record).get(settings.ackTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        } catch (TimeoutException exception) {
            retry(event, "KAFKA_ACK_TIMEOUT");
            return;
        } catch (ExecutionException | RuntimeException exception) {
            recordKafkaFailure(event, exception);
            return;
        }
        if (result == null || !result.getRecordMetadata().hasOffset()) {
            retry(event, "KAFKA_ACK_MISSING_OFFSET");
        } else if (!publications.sent(event)) {
            log.warn("Inventory Outbox ACK arrived after claim changed. eventId={}", event.eventId());
        }
    }

    private void recordKafkaFailure(PollingInventoryEvent event, Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RecordTooLargeException || cause instanceof InvalidTopicException
                    || cause instanceof SerializationException) {
                quarantine(event, "KAFKA_TERMINAL_RECORD");
                return;
            }
        }
        retry(event, "KAFKA_SEND_FAILED");
    }

    private void retry(PollingInventoryEvent event, String code) {
        long delay = settings.retryDelayMs(event.attempts());
        if (publications.retry(event, delay, code)
                && (event.attempts() == 1 || event.attempts() % 10 == 0)) {
            log.warn("Inventory Outbox retry. eventId={} attempts={} code={}",
                    event.eventId(), event.attempts(), code);
        }
    }

    private void quarantine(PollingInventoryEvent event, String code) {
        if (publications.quarantine(event, code)) {
            log.warn("Inventory Outbox event quarantined. eventId={} code={}", event.eventId(), code);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        closeProducer.run();
    }
}
