package org.hvtra.orderservice.outbox.event;

public final class OrderEventType {

    private OrderEventType() {
    }

    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String ORDER_PAYMENT_SUCCEEDED = "ORDER_PAYMENT_SUCCEEDED";
    public static final String ORDER_PAYMENT_FAILED = "ORDER_PAYMENT_FAILED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String INVENTORY_RESERVATION_CONFIRM_REQUESTED = "INVENTORY_RESERVATION_CONFIRM_REQUESTED";
    public static final String CUSTOMER_PURCHASE_UPDATE_REQUESTED = "CUSTOMER_PURCHASE_UPDATE_REQUESTED";
    public static final String PROMOTION_USAGE_COMMIT_REQUESTED = "PROMOTION_USAGE_COMMIT_REQUESTED";
}
