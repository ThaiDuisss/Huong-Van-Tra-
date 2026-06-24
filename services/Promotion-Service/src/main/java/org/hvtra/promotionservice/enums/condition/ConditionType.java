package org.hvtra.promotionservice.enums.condition;

public enum ConditionType {
    MIN_ORDER_AMOUNT, // don toi thieu
    MAX_ORDER_AMOUNT, // don toi da
    CUSTOMER_TIER, // loai khach hang: VIP, GOLD
    PRODUCT_ID, // chi ap dung san pham A
    CATEGORY_ID, // chi ap dung loai A
    CHANNEL, // ONLINE, POS
    BRANCH, // Chi nhanh
    PAYMENT_METHOD // phuong thuc thanh toan
}