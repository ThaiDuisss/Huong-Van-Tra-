package org.hvtra.promotionservice.enums.coupon;

public enum CouponUsageStatus {
    RESERVED,   // giữ mã tạm thời khi order đang pending payment
    APPLIED,    // đã áp dụng thành công
    RELEASED,   // nhả mã do order bị hủy/thanh toán fail
    EXPIRED,    // reservation hết hạn
    CANCELLED   // hủy usage
}