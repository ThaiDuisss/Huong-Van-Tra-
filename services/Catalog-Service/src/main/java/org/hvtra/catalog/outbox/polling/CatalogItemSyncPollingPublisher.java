package org.hvtra.catalog.outbox.polling;

import com.fasterxml.jackson.core.JsonProcessingException;
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
final class CatalogItemSyncPollingPublisher implements AutoCloseable {
    private final CatalogItemSyncPublicationStore publications;
    private final CatalogItemSyncRecordFactory records;
    private final KafkaOperations<String, String> kafka;
    private final CatalogItemSyncProperties settings;
    private final Runnable closeProducer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("catalog-item-sync-poll").daemon().factory());

    CatalogItemSyncPollingPublisher(CatalogItemSyncPublicationStore publications, CatalogItemSyncRecordFactory records,
                                    KafkaOperations<String, String> kafka, CatalogItemSyncProperties settings,
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
            for (long sequence : publications.due(settings.activationId(), settings.batchSize())) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                publications.claim(sequence, settings.activationId(), settings.leaseMs()).ifPresent(this::publish);
            }
        } catch (RuntimeException exception) {
            log.warn("Catalog ITEM_SYNC polling interrupted by {}", exception.getClass().getSimpleName());
        }
    }

    private void publish(CatalogItemSyncEvent event) {
        ProducerRecord<String, String> record;
        try {
            record = records.create(event);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            quarantine(event, "INVALID_OUTBOX_RECORD");
            return;
        }

        if (Thread.currentThread().isInterrupted() || !publications.canSend(event, settings.activationId())) {
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
            return;
        }
        var ack = result.getRecordMetadata();
        if (!publications.sent(event, ack.partition(), ack.offset())) {
            log.warn("Catalog ITEM_SYNC ACK arrived after claim changed. eventId={}", event.eventId());
        }
    }

    private void recordKafkaFailure(CatalogItemSyncEvent event, Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RecordTooLargeException || cause instanceof InvalidTopicException
                    || cause instanceof SerializationException) {
                quarantine(event, "KAFKA_TERMINAL_RECORD");
                return;
            }
        }
        retry(event, "KAFKA_SEND_FAILED");
    }

    private void retry(CatalogItemSyncEvent event, String code) {
        if (publications.retry(event, settings.retryDelayMs(event.attempts()), code)
                && (event.attempts() == 1 || event.attempts() % 10 == 0)) {
            log.warn("Catalog ITEM_SYNC retry. eventId={} attempts={} code={}", event.eventId(), event.attempts(), code);
        }
    }

    private void quarantine(CatalogItemSyncEvent event, String code) {
        if (publications.quarantine(event, code)) {
            log.warn("Catalog ITEM_SYNC stream blocked. eventId={} code={}", event.eventId(), code);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        closeProducer.run();
    }
}
