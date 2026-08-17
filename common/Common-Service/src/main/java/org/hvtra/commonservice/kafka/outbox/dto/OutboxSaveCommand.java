package org.hvtra.commonservice.kafka.outbox.dto;

public record OutboxSaveCommand(
        String eventId,
        String aggregateType,
        String aggregateId,
        String messageKey,
        String eventType,
        String businessKey,
        String topic,
        String sourceService,
        String sagaId,
        String correlationId,
        String causationId,
        Object payload,
        Object headers
) {}
