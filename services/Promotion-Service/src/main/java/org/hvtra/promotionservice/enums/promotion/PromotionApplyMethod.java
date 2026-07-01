package org.hvtra.promotionservice.enums.promotion;

public enum PromotionApplyMethod {
    AUTO,             // hệ thống tự áp dụng khi đủ điều kiện
    COUPON_CODE,      // cần nhập mã coupon
    DIRECT_DISCOUNT   // giảm trực tiếp trên đơn POS, không cần account/coupon
}