package org.hvtra.promotionservice.enums.coupon;

public enum CouponStatus {
    DRAFT,      // mới tạo, chưa phát hành/chưa cho dùng

    SCHEDULED,  // đã tạo mã, chờ đến thời điểm phát hành hoặc thời điểm promotion bắt đầu

    ACTIVE,     // đang có thể sử dụng

    PAUSED,     // tạm dừng sử dụng, có thể mở lại sau

    EXPIRED,    // hết hạn sử dụng

    CANCELLED,  // hủy mã, không cho dùng nữa

    ARCHIVED    // lưu trữ, không hiển thị chính nhưng vẫn giữ lịch sử
}