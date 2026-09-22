package org.hvtra.commonservice.kafka.producer.dto;

public record SagaMessage<T>(
        SagaMetadata metadata,
        T payload
) {}