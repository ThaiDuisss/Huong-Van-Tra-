package org.hvtra.commonservice.kafka.producer.dto;

import org.hvtra.commonservice.kafka.saga.enums.SagaAction;

import java.time.Instant;
import java.util.UUID;

public record SagaMetadata(
        UUID messageId,
        UUID sagaId,
        String sagaType,
        UUID aggregateId,
        String aggregateType,
        String step,
        SagaAction action,
        Instant createdAt
) {}

