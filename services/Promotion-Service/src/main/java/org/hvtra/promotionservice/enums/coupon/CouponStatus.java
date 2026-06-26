package org.hvtra.promotionservice.enums.coupon;

public enum CouponStatus {
    DRAFT,      // mới tạo, chưa dùng
    ACTIVE,     // đang có thể sử dụng
    PAUSED,     // tạm dừng
    EXPIRED,    // hết hạn
    CANCELLED,  // hủy
    ARCHIVED    // lưu trữ, không hiển thị chính
}