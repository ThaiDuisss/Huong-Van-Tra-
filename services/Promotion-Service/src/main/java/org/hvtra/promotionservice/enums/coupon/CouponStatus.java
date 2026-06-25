package org.hvtra.promotionservice.enums.coupon;

public enum CouponStatus {
    DRAFT, // moi tao
    ACTIVE, // dua vao su dung
    PAUSED, // tam dung co the sua
    CANCELLED, // hủy coupon vì không muốn cho nó tiếp tục tồn tại/tiếp tục chạy
    ARCHIVED  // cất coupon cũ vào kho lưu trữ, không còn hiển thị ở màn quản lý chính
}