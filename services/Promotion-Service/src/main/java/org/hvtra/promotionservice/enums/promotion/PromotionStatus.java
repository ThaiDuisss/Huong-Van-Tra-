package org.hvtra.promotionservice.enums.promotion;

public enum PromotionStatus {
    DRAFT,      // mới tạo, chưa sẵn sàng áp dụng

    SCHEDULED,  // đã cấu hình xong, chờ đến startAt để tự động kích hoạt

    ACTIVE,     // đang áp dụng được

    INACTIVE,   // tạm khóa, chưa cho áp dụng dù còn thời gian

    EXPIRED,    // đã hết thời gian áp dụng

    CANCELLED   // bị hủy, không dùng lại nữa
}