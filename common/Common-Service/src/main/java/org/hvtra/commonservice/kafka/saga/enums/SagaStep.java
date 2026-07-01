package org.hvtra.commonservice.kafka.saga.enums;

public enum SagaStep {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    SKIPPED
}