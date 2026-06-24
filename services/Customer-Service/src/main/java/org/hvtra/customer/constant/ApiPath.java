package org.hvtra.customer.constant;

public final class ApiPath {
    public static final String CUSTOMER_BASE = "/api/customer";
    public static final String CUSTOMER_ADDRESSES = CUSTOMER_BASE + "/addresses";
    public static final String CUSTOMER_TIERS = CUSTOMER_BASE + "/tiers";
    public static final String LOYALTY_ACCOUNTS = CUSTOMER_BASE + "/loyalty-accounts";
    public static final String LOYALTY_TRANSACTIONS = CUSTOMER_BASE + "/loyalty-transactions";
    public static final String PURCHASE_UPDATE = "/purchases/{orderId}";

    public static final String ID = "/{id}";
    public static final String CUSTOMER_ID = "/customer/{customerId}";

    private ApiPath() {
    }
}
