package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.coupon.CouponCreateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponStatusUpdateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponUpdateRequest;
import org.hvtra.promotionservice.dto.response.CouponResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;

import java.util.UUID;

public interface CouponService {

    CouponResponse createCoupon(UUID promotionId, CouponCreateRequest request);

    CouponResponse getCouponById(UUID couponId);

    PageResponse<CouponResponse> getCouponsByPromotion(UUID promotionId, int page, int size);

    CouponResponse updateCoupon(UUID couponId, CouponUpdateRequest request);

    CouponResponse updateCouponStatus(UUID couponId, CouponStatusUpdateRequest request);

    void deleteCoupon(UUID couponId);

}
