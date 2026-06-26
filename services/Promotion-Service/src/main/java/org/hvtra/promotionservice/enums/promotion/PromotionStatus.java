package org.hvtra.promotionservice.enums.promotion;

public enum PromotionStatus {
    DRAFT,      // mới tạo, chưa sẵn sàng áp dụng
    ACTIVE,     // đang áp dụng được
    INACTIVE,   // tạm khóa
    EXPIRED,    // hết hạn
    CANCELLED   // bị hủy
}