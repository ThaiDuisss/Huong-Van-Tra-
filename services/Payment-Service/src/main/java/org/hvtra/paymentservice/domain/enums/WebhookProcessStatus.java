package org.hvtra.paymentservice.domain.enums;

public enum WebhookProcessStatus {
    RECEIVED,
    DUPLICATE,
    PROCESSING,
    PROCESSED,
    FAILED,
    NEED_REVIEW,
    AUTH_FAILED
}