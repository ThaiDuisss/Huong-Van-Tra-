package org.hvtra.customer.constant;

public final class ApiPath {

    public static final String CUSTOMER_BASE = "/api/customer";
    public static final String CUSTOMER_TIERS = CUSTOMER_BASE + "/tiers";

    public static final String ID = "/{id}";
    public static final String CUSTOMER_ID = "/{customerId}";
    public static final String CUSTOMER_STATUS = ID + "/status";
    public static final String CUSTOMER_IMPORT = "/import";
    public static final String CUSTOMER_LOOKUP = "/lookup";
    public static final String TIER_STATUS = ID + "/status";

    public static final String LOYALTY_TRANSACTIONS = "/loyalty-transactions";
    public static final String LOYALTY_ADJUSTMENTS = "/loyalty-adjustments";

    private ApiPath() {
    }
}
