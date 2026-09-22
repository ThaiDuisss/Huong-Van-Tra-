package org.hvtra.commonservice.common_core.kafka.saga.enums;

public enum CompensationStatus {
    NONE,
    REQUIRED,
    PROCESSING,
    COMPLETED,
    FAILED
}