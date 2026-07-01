package org.hvtra.promotionservice.enums.coupon;

public enum CouponIssueReason {
    MANUAL,                 // admin/nhân viên phát thủ công
    ORDER_AMOUNT_REACHED,   // khách đạt mốc tổng tiền đơn
    ORDER_COUNT_REACHED,    // khách mua đủ số đơn
    LOYALTY_POINT_REDEEMED, // khách đổi điểm lấy mã
    FIRST_PURCHASE          // khách mua lần đầu
}