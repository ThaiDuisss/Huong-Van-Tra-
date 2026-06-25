package org.hvtra.promotionservice.constant.couponusage;

public final class CouponUsageErrorMessage {

    private CouponUsageErrorMessage() {
    }

    public static final String COUPON_USAGE_NOT_FOUND_WITH_ID =
            "Coupon usage not found with id: %s";

    public static final String COUPON_NOT_FOUND_WITH_CODE =
            "Coupon not found with code: %s";

    public static final String COUPON_NOT_ACTIVE =
            "Coupon is not active";

    public static final String COUPON_EXPIRED =
            "Coupon is expired";

    public static final String COUPON_USAGE_LIMIT_EXCEEDED =
            "Coupon usage limit exceeded";

    public static final String COUPON_ALREADY_RESERVED_FOR_ORDER =
            "Coupon already reserved for this order";

    public static final String COUPON_USAGE_NOT_RESERVED =
            "Only reserved coupon usage can be changed";
}
