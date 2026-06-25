package org.hvtra.orderservice.domain.enums;

public enum OrderStatus {
    DRAFT,
    PENDING_PAYMENT,
    PAYMENT_CONFIRMED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    PAYMENT_FAILED
}
