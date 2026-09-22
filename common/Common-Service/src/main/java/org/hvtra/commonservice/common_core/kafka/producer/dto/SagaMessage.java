package org.hvtra.commonservice.common_core.kafka.producer.dto;

public record SagaMessage<T>(
        SagaMetadata metadata,
        T payload
) {}