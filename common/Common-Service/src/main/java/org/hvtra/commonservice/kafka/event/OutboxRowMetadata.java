package org.hvtra.commonservice.kafka.event;

public record OutboxRowMetadata(
        String eventId,
        String eventType,
        String eventKey,
        String aggregateType,
        String aggregateId,
        String sagaId,
        String correlationId,
        String causationId,
        String sourceService,
        String topic
) {}
