package org.hvtra.orderservice.domain.enums;

public enum RefundProgressStatus {
    WAITING_RECEIPT,
    WAITING_INSPECTION,
    WAITING_REFUND,
    REFUNDED,
    FAILED,
    OTHER
}
