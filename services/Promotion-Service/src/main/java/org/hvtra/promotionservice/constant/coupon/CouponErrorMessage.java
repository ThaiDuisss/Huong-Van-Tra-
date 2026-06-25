package org.hvtra.promotionservice.constant.coupon;

public final class CouponErrorMessage {

    private CouponErrorMessage() {
    }

    public static final String COUPON_NOT_FOUND_WITH_ID =
            "Coupon not found with id: %s";

    public static final String COUPON_CODE_ALREADY_EXISTS =
            "Coupon code already exists: %s";

    public static final String COUPON_NOT_BELONG_TO_PROMOTION =
            "Coupon does not belong to promotion: %s";
}
