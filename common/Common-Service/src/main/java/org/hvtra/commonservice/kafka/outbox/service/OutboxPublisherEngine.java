package org.hvtra.commonservice.kafka.outbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hvtra.commonservice.kafka.outbox.entity.BaseOutboxEvent;
import org.hvtra.commonservice.kafka.outbox.repository.BaseOutboxEventRepository;
import org.hvtra.commonservice.kafka.outbox.resolver.OutboxTopicResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@RequiredArgsConstructor
public class OutboxPublisherEngine<T extends BaseOutboxEvent> {

    private final BaseOutboxEventRepository<T> outboxEventRepository;
    private final OutboxClaimService<T> outboxClaimService;
    private final OutboxTopicResolver outboxTopicResolver;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.kafka-timeout-seconds:10}")
    private long kafkaTimeoutSeconds;

    @Value("${app.outbox.stale-minutes:5}")
    private long staleMinutes;

//    public void publishPendingEvents(String workerId) {
//        List<UUID> ids = outboxEventRepository.findIdsByStatus(
//                OutboxStatus.PENDING,
//                PageRequest.of(0, batchSize)
//        );
//
//        for (UUID id : ids) {
//            publishOne(id, workerId);
//        }
//
//        publishStaleProcessingEvents(workerId);
//    }
//
//    private void publishStaleProcessingEvents(String workerId) {
//        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(staleMinutes));
//
//        List<UUID> staleIds = outboxEventRepository.findStaleProcessingIds(
//                OutboxStatus.PROCESSING,
//                staleBefore,
//                PageRequest.of(0, batchSize)
//        );
//
//        for (UUID id : staleIds) {
//            publishStaleOne(id, workerId, staleBefore);
//        }
//    }

//    private void publishOne(UUID eventId, String workerId) {
//        Optional<ClaimedOutboxMessage> optionalMessage =
//                outboxClaimService.claim(eventId, workerId);
//
//        optionalMessage.ifPresent(this::sendToKafka);
//    }
//
//    private void publishStaleOne(UUID eventId, String workerId, Instant staleBefore) {
//        Optional<ClaimedOutboxMessage> optionalMessage =
//                outboxClaimService.reclaimStale(eventId, workerId, staleBefore);
//
//        optionalMessage.ifPresent(this::sendToKafka);
//    }

//    private void sendToKafka(ClaimedOutboxMessage message) {
//        String topic = outboxTopicResolver.resolveTopic(message.eventType());
//
//        try {
//            SendResult<String, String> result = kafkaTemplate
//                    .send(topic, message.eventKey(), message.payload())
//                    .get(kafkaTimeoutSeconds, TimeUnit.SECONDS);
//
//            outboxClaimService.markSent(
//                    message.id(),
//                    result.getRecordMetadata().topic(),
//                    result.getRecordMetadata().partition(),
//                    result.getRecordMetadata().offset()
//            );
//
//        } catch (Exception ex) {
//            log.error("Publish outbox event failed. eventId={}", message.id(), ex);
//
//            outboxClaimService.markFailed(
//                    message.id(),
//                    ex.getMessage()
//            );
//        }
//    }
}