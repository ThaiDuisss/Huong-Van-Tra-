package org.hvtra.orderservice.domain.enums;

public enum PaymentStatus {
    UNPAID,
    WAITING_PAYMENT,
    PAID,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED
}