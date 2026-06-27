package org.hvtra.customer.constant;

public final class ErrorCode {
    public static final String CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND";
    public static final String CUSTOMER_CONFLICT = "CUSTOMER_CONFLICT";
    public static final String CUSTOMER_INVALID_REQUEST = "CUSTOMER_INVALID_REQUEST";

    public static final String ORDER_SERVICE_UNAVAILABLE = "ORDER_SERVICE_UNAVAILABLE";

    private ErrorCode() {
    }
}
