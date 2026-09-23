package org.hvtra.commonservice.common_core.kafka.event;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record EventMetadata(
        UUID messageId,

        String eventType,
        Integer eventVersion,

        String sourceService,

        String aggregateType,
        UUID aggregateId,

        UUID sagaId,

        String correlationId,
        String causationId,

        LocalDateTime occurredAt
) {

    public static EventMetadata create(
            String eventType,
            Integer eventVersion,
            String sourceService,
            String aggregateType,
            UUID aggregateId,
            UUID sagaId,
            String correlationId,
            String causationId
    ) {
        return EventMetadata.builder()
                .messageId(UUID.randomUUID())
                .eventType(eventType)
                .eventVersion(eventVersion)
                .sourceService(sourceService)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .sagaId(sagaId)
                .correlationId(correlationId)
                .causationId(causationId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}