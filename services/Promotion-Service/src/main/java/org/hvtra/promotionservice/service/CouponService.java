package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.coupon.*;
import org.hvtra.promotionservice.dto.response.CouponResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

public interface CouponService {

    CouponResponse createCoupon(CouponCreateRequest request);

    CouponResponse createCouponByPromotion(UUID promotionId, CouponCreateRequest request);

    CouponResponse getCouponById(UUID couponId);

    PageResponse<CouponResponse> getAllCoupons(int page, int size);

    PageResponse<CouponResponse> getCoupons(CouponFilterRequest request, int page, int size);

    PageResponse<CouponResponse> getCouponsByPromotion(UUID promotionId, int page, int size);

    CouponResponse updateCoupon(UUID couponId, CouponUpdateRequest request);

    CouponResponse updateCouponStatus(UUID couponId, CouponStatusUpdateRequest request);

    void deleteCoupon(UUID couponId);

    CouponResponse activateCoupon(UUID couponId);

    CouponResponse pauseCoupon(UUID couponId);

    CouponResponse cancelCoupon(UUID couponId);

    CouponResponse expireCoupon(UUID couponId);

    CouponResponse archiveCoupon(UUID couponId);

    CouponResponse generatePublicCoupon(UUID promotionId, CouponGeneratePublicRequest request);

    CouponResponse generatePersonalCoupon(UUID promotionId, CouponGeneratePersonalRequest request);

    List<CouponResponse> bulkGenerateCoupons(UUID promotionId, CouponBulkGenerateRequest request);
}
