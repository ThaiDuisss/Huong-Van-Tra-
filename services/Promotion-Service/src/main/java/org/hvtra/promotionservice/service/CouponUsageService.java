package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;

import java.util.UUID;

public interface CouponUsageService {

    CouponUsageResponse reserveCoupon(CouponUsageReserveRequest request);

    CouponUsageResponse confirmCouponUsage(UUID usageId);

    CouponUsageResponse releaseCouponUsage(UUID usageId);

    CouponUsageResponse getCouponUsageById(UUID usageId);

    PageResponse<CouponUsageResponse> getCouponUsagesByCoupon(UUID couponId, int page, int size);

    PageResponse<CouponUsageResponse> getCouponUsagesByCustomer(UUID customerId, int page, int size);
}
