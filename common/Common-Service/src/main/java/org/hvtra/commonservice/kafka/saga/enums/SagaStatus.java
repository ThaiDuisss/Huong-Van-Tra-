package org.hvtra.commonservice.kafka.saga.enums;

public enum SagaStatus {
    STARTED,
    PROCESSING,
    WAITING_REPLY,
    COMPENSATING,
    COMPENSATED,
    COMPLETED,
    FAILED}
