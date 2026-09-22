package org.hvtra.commonservice.kafka.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        MessageKind messageKind,

        String sourceService,
        String targetService,

        String aggregateType,
        UUID aggregateId,

        UUID sagaId,
        UUID correlationId,
        UUID causationId,

        Instant occurredAt,

        T payload,
        Map<String, String> headers
) {
}