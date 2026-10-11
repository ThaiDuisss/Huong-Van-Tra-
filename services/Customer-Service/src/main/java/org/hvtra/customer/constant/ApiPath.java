package org.hvtra.customer.constant;

public final class ApiPath {
    public static final String CUSTOMER_BASE = "/api/customer";
    public static final String CUSTOMER_ADDRESSES = CUSTOMER_BASE + "/addresses";
    public static final String CUSTOMER_TIERS = CUSTOMER_BASE + "/tiers";
    public static final String LOYALTY_ACCOUNTS = CUSTOMER_BASE + "/loyalty-accounts";
    public static final String LOYALTY_TRANSACTIONS = CUSTOMER_BASE + "/loyalty-transactions";
    public static final String LOYALTY_POINT_RULES = CUSTOMER_BASE + "/loyalty-point-rules";
    public static final String PURCHASE_UPDATE = "/purchases/{orderId}";
    public static final String TIER_HISTORY = "/tier-history";
    public static final String TIER_METRICS = "/tier-metrics";
    public static final String PURCHASE_TRANSACTIONS = "/purchase-transactions";
    public static final String TIER_PROGRESS = "/tier-progress";
    public static final String TIER_ADJUSTMENTS = "/tier-adjustments";

    public static final String ID = "/{id}";
    public static final String TIER_STATUS = ID + "/status";
    public static final String CUSTOMER_STATUS = ID + "/status";
    public static final String CUSTOMER_IMPORT = "/import";
    public static final String CUSTOMER_ID = "/customer/{customerId}";

    private ApiPath() {
    }
}
