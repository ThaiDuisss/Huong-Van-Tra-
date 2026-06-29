package org.hvtra.commonservice.kafka.outbox.service;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.outbox.dto.ClaimedOutboxMessage;
import org.hvtra.commonservice.kafka.outbox.entity.BaseOutboxEvent;
import org.hvtra.commonservice.kafka.outbox.enums.OutboxStatus;
import org.hvtra.commonservice.kafka.outbox.repository.BaseOutboxEventRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class OutboxClaimService<T extends BaseOutboxEvent> {
    private final BaseOutboxEventRepository<T> outboxEventRepository;

    @Transactional
    public Optional<ClaimedOutboxMessage> claim(UUID eventId, String workerId) {
        Instant now = Instant.now();

        int updated = outboxEventRepository.claimPending(
                eventId,
                workerId,
                now,
                now,
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING
        );

        if (updated == 0) {
            return Optional.empty();
        }

        return outboxEventRepository.findMessageById(eventId)
                .map(this::toClaimedMessage);
    }

    @Transactional
    public Optional<ClaimedOutboxMessage> reclaimStale(
            UUID eventId,
            String workerId,
            Instant staleBefore
    ) {
        Instant now = Instant.now();

        int updated = outboxEventRepository.reclaimStale(
                eventId,
                workerId,
                now,
                now,
                staleBefore,
                OutboxStatus.PROCESSING
        );

        if (updated == 0) {
            return Optional.empty();
        }

        return outboxEventRepository.findMessageById(eventId)
                .map(this::toClaimedMessage);
    }

    @Transactional
    public void markSent(
            UUID eventId,
            String workerId,
            String topic,
            Integer partitionNo,
            Long offsetNo
    ) {
        Instant now = Instant.now();

        int updated = outboxEventRepository.markSent(
                eventId,
                workerId,
                topic,
                partitionNo,
                offsetNo,
                now,
                now,
                OutboxStatus.PROCESSING,
                OutboxStatus.SENT
        );

        if (updated == 0) {
            throw new IllegalStateException(
                    "Cannot mark outbox event as SENT. eventId=" + eventId + ", workerId=" + workerId
            );
        }
    }

    @Transactional
    public void markFailed(
            UUID eventId,
            String workerId,
            String errorMessage
    ) {
        Instant now = Instant.now();

        int updated = outboxEventRepository.markFailed(
                eventId,
                workerId,
                normalizeError(errorMessage),
                now,
                OutboxStatus.PROCESSING,
                OutboxStatus.PENDING,
                OutboxStatus.FAILED
        );

        if (updated == 0) {
            throw new IllegalStateException(
                    "Cannot mark outbox event as FAILED/PENDING. eventId=" + eventId + ", workerId=" + workerId
            );
        }
    }


    private ClaimedOutboxMessage toClaimedMessage(T event) {
        return new ClaimedOutboxMessage(
                event.getId(),
                event.getEventType(),
                event.getEventKey(),
                event.getPayload()
        );
    }
    private String normalizeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Unknown error";
        }

        int maxLength = 4000;
        return errorMessage.length() <= maxLength
                ? errorMessage
                : errorMessage.substring(0, maxLength);
    }

}
