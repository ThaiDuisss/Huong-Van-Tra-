package org.hvtra.orderservice.outbox.polling;

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
final class OrderOutboxPollingPublisher implements AutoCloseable {
    private final OrderOutboxPublicationStore publications;
    private final OrderOutboxRecordFactory records;
    private final KafkaOperations<String, String> kafka;
    private final OrderOutboxPollingProperties settings;
    private final Runnable closeProducer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("order-outbox-poll").daemon().factory());

    OrderOutboxPollingPublisher(OrderOutboxPublicationStore publications, OrderOutboxRecordFactory records,
                                KafkaOperations<String, String> kafka, OrderOutboxPollingProperties settings,
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
            log.warn("Order Outbox polling interrupted by {}", exception.getClass().getSimpleName());
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

    private void publish(PollingOrderEvent event) {
        ProducerRecord<String, String> record;
        try {
            record = records.create(event);
        } catch (OrderOutboxRecordFactory.RejectedEvent exception) {
            if (exception.retryable) {
                retry(event, exception.getMessage());
            } else {
                quarantine(event, exception.getMessage());
            }
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
            log.warn("Order Outbox ACK arrived after claim changed. eventId={}", event.eventId());
        }
    }

    private void recordKafkaFailure(PollingOrderEvent event, Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RecordTooLargeException || cause instanceof InvalidTopicException
                    || cause instanceof SerializationException) {
                quarantine(event, "KAFKA_TERMINAL_RECORD");
                return;
            }
        }
        retry(event, "KAFKA_SEND_FAILED");
    }

    private void retry(PollingOrderEvent event, String code) {
        long delay = settings.retryDelayMs(event.attempts());
        if (publications.retry(event, delay, code)
                && (event.attempts() == 1 || event.attempts() % 10 == 0)) {
            log.warn("Order Outbox retry. eventId={} attempts={} code={}", event.eventId(), event.attempts(), code);
        }
    }

    private void quarantine(PollingOrderEvent event, String code) {
        if (publications.quarantine(event, code)) {
            log.warn("Order Outbox event quarantined. eventId={} code={}", event.eventId(), code);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        closeProducer.run();
    }
}
