package org.hvtra.commonservice.kafka.outbox.enums;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    COMPENSATING,
    COMPENSATED
}