package org.hvtra.commonservice.kafka.producer.dto;

import org.hvtra.commonservice.kafka.saga.enums.SagaReplyStatus;

public record SagaReplyMessage<T>(
        SagaMetadata metadata,
        SagaReplyStatus status,
        String errorCode,
        String errorMessage,
        T payload
) {}
