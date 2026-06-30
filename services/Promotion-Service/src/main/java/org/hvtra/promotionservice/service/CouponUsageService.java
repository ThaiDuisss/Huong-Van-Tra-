package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;

import java.util.UUID;

public interface CouponUsageService {

    PageResponse<CouponUsageResponse> getAllCouponUsages(int page, int size);

    CouponUsageResponse getCouponUsageById(UUID usageId);

    PageResponse<CouponUsageResponse> getUsagesByCoupon(UUID couponId, int page, int size);

    PageResponse<CouponUsageResponse> getUsagesByOrder(UUID orderId, int page, int size);

    PageResponse<CouponUsageResponse> getUsagesByCustomer(UUID customerId, int page, int size);

    CouponUsageResponse releaseUsage(UUID usageId);

    CouponUsageResponse cancelUsage(UUID usageId);

    CouponUsageResponse expireUsage(UUID usageId);
}