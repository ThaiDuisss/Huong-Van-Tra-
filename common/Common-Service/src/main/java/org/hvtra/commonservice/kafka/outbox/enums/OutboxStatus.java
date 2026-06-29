package org.hvtra.commonservice.outbox.enums;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    COMPENSATING,
    COMPENSATED
}