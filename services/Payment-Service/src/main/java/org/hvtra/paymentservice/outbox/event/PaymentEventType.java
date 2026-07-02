package org.hvtra.paymentservice.outbox.event;

public final class PaymentEventType {

    private PaymentEventType() {}

    public static final String PAYMENT_WAITING_PAYMENT = "PAYMENT_WAITING_PAYMENT";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_EXPIRED = "PAYMENT_EXPIRED";
    public static final String PAYMENT_CANCELLED = "PAYMENT_CANCELLED";
    public static final String PAYMENT_NEED_REVIEW = "PAYMENT_NEED_REVIEW";
}