package org.hvtra.promotionservice.constant;

public class ApiPath {

    private ApiPath() {
    }

    public static final String API = "/api";

    // Prefix domain/service
    public static final String PROMOTION_SERVICE = API + "/promotions";

    // Resources
    public static final String EVENTS = PROMOTION_SERVICE + "/events";
    public static final String CAMPAIGNS = PROMOTION_SERVICE + "/campaigns";
    public static final String COUPONS = PROMOTION_SERVICE + "/coupons";
    public static final String COUPON_USAGES = PROMOTION_SERVICE + "/coupon-usages";
}
