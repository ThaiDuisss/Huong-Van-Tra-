package org.hvtra.commonservice.common_core.kafka.producer.dto;

import org.hvtra.commonservice.common_core.kafka.saga.enums.SagaAction;

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

