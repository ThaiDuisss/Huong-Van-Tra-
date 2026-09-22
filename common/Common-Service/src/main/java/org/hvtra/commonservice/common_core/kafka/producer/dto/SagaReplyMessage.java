package org.hvtra.commonservice.common_core.kafka.producer.dto;

import org.hvtra.commonservice.common_core.kafka.saga.enums.SagaReplyStatus;

public record SagaReplyMessage<T>(
        SagaMetadata metadata,
        SagaReplyStatus status,
        String errorCode,
        String errorMessage,
        T payload
) {}
