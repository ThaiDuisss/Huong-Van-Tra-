package org.hvtra.commonservice.kafka.saga.enums;

public enum CompensationStatus {
    NONE,
    REQUIRED,
    PROCESSING,
    COMPLETED,
    FAILED
}