package org.hvtra.paymentservice.domain.enums;

public enum PaymentAttemptStatus {
    CREATED,
    WAITING,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    CANCELLED,
    NEED_REVIEW
}