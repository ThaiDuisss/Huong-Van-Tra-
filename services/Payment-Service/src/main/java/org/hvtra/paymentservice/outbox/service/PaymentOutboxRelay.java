package org.hvtra.paymentservice.outbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.kafka.outbox.dto.ClaimedOutboxMessage;
import org.hvtra.commonservice.kafka.outbox.enums.OutboxStatus;
import org.hvtra.commonservice.kafka.outbox.service.OutboxClaimService;
import org.hvtra.paymentservice.outbox.entity.OutboxEvent;
import org.hvtra.paymentservice.outbox.repository.OutboxEventRepository;
import org.hvtra.paymentservice.outbox.resolver.PaymentOutboxTopicResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxClaimService<OutboxEvent> outboxClaimService;
    private final PaymentOutboxTopicResolver topicResolver;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.kafka-timeout-seconds:10}")
    private long kafkaTimeoutSeconds;

    @Value("${app.outbox.stale-minutes:5}")
    private long staleMinutes;

    @Value("${spring.application.name:payment-service}")
    private String applicationName;

    private final String workerId = "payment-outbox-" + UUID.randomUUID();

    @Scheduled(
            fixedDelayString = "${app.outbox.fixed-delay-ms:3000}",
            initialDelayString = "${app.outbox.initial-delay-ms:5000}"
    )
    public void publishOutboxEvents() {
        publishPendingEvents();
        publishStaleProcessingEvents();
    }

    private void publishPendingEvents() {
        List<UUID> ids = outboxEventRepository.findIdsByStatus(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );

        for (UUID id : ids) {
            Optional<ClaimedOutboxMessage> message = outboxClaimService.claim(id, resolveWorkerId());
            message.ifPresent(this::sendToKafka);
        }
    }

    private void publishStaleProcessingEvents() {
        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(staleMinutes));

        List<UUID> ids = outboxEventRepository.findStaleProcessingIds(
                OutboxStatus.PROCESSING,
                staleBefore,
                PageRequest.of(0, batchSize)
        );

        for (UUID id : ids) {
            Optional<ClaimedOutboxMessage> message = outboxClaimService.reclaimStale(
                    id,
                    resolveWorkerId(),
                    staleBefore
            );
            message.ifPresent(this::sendToKafka);
        }
    }

    private void sendToKafka(ClaimedOutboxMessage message) {
        String topic = topicResolver.resolveTopic(message.eventType());
        String worker = resolveWorkerId();

        try {
            SendResult<String, String> result = kafkaTemplate
                    .send(topic, message.kafkaKey(), message.payload())
                    .get(kafkaTimeoutSeconds, TimeUnit.SECONDS);

            outboxClaimService.markSent(
                    message.id(),
                    worker,
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );

            log.info(
                    "Published payment outbox event. eventId={}, eventType={}, topic={}, partition={}, offset={}",
                    message.id(),
                    message.eventType(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        } catch (Exception ex) {
            log.error(
                    "Publish payment outbox event failed. eventId={}, eventType={}, topic={}",
                    message.id(),
                    message.eventType(),
                    topic,
                    ex
            );

            outboxClaimService.markFailed(
                    message.id(),
                    worker,
                    ex.getMessage()
            );
        }
    }

    private String resolveWorkerId() {
        return applicationName + ":" + workerId;
    }
}
