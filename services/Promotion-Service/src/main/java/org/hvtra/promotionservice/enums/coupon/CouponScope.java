package org.hvtra.promotionservice.enums.coupon;

public enum CouponScope {
    PUBLIC,     // mã all, ai biết mã cũng có thể dùng nếu đủ điều kiện
    PERSONAL    // mã cá nhân, chỉ customer_id được gán mới dùng được
}