package org.hvtra.promotionservice.enums.condition;

public enum ConditionType {

    // Điều kiện theo đơn hàng
    MIN_ORDER_AMOUNT,
    MAX_ORDER_AMOUNT,
    MIN_ITEM_QUANTITY,

    // Điều kiện theo lịch sử khách hàng
    MIN_ORDER_COUNT,
    MIN_TOTAL_SPENT,
    MIN_LOYALTY_POINT,
    FIRST_PURCHASE,

    // Điều kiện theo khách hàng
    CUSTOMER_TIER,
    CUSTOMER_ID,

    // Điều kiện theo ngữ cảnh bán hàng
    CHANNEL,
    BRANCH,
    PAYMENT_METHOD
}