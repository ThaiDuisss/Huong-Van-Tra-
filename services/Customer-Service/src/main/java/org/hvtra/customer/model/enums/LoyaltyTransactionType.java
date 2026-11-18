package org.hvtra.customer.model.enums;

public enum LoyaltyTransactionType {
    /** Tích điểm khi đơn hoàn tất. */
    EARN,
    /** Trừ lại điểm đã tích khi đơn bị hoàn tiền. */
    REVERSE,
    /** Quản lý cộng / trừ điểm thủ công. */
    ADJUST,
    /** Dùng điểm để thanh toán (chưa triển khai). */
    REDEEM
}
