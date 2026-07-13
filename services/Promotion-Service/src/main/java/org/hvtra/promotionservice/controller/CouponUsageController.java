package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.promotionservice.constant.ApiPath;
import org.hvtra.promotionservice.constant.couponusage.CouponUsageSuccessMessage;
import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageFilterRequest;
import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.service.CouponUsageService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPath.PROMOTION_SERVICE)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Coupon usage management",
        description = "APIs for coupon reservation, payment confirmation and usage history."
)
public class CouponUsageController {

    CouponUsageService couponUsageService;

    @Operation(summary = "Get coupon usage list")
    @GetMapping("/coupon-usages")
    public ApiResponse<PageResponse<CouponUsageResponse>> getAllCouponUsages(
            @Valid @ModelAttribute CouponUsageFilterRequest filter,

            @Parameter(description = "Page index, starting from 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponUsageResponse> response =
                couponUsageService.getCouponUsages(filter, page, size);

        return ApiResponse.success(CouponUsageSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Reserve coupon",
            description = "Reserve a coupon for pending payment and temporarily decrease available usage quota."
    )
    @PostMapping("/coupon-usages/reserve")
    public ApiResponse<CouponUsageResponse> reserveCoupon(
            @Valid @RequestBody CouponUsageReserveRequest request
    ) {
        CouponUsageResponse response = couponUsageService.reserveCoupon(request);
        return ApiResponse.created(CouponUsageSuccessMessage.RESERVED, response);
    }

    @Operation(summary = "Get coupon usage detail")
    @GetMapping("/coupon-usages/{usageId}")
    public ApiResponse<CouponUsageResponse> getCouponUsageById(
            @Parameter(description = "Coupon usage id", required = true)
            @PathVariable UUID usageId
    ) {
        CouponUsageResponse response = couponUsageService.getCouponUsageById(usageId);
        return ApiResponse.success(CouponUsageSuccessMessage.FETCHED, response);
    }

    @Operation(summary = "Get coupon usages by coupon")
    @GetMapping("/coupons/{couponId}/usages")
    public ApiResponse<PageResponse<CouponUsageResponse>> getUsagesByCoupon(
            @Parameter(description = "Coupon id", required = true)
            @PathVariable UUID couponId,

            @Parameter(description = "Page index, starting from 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponUsageResponse> response =
                couponUsageService.getUsagesByCoupon(couponId, page, size);

        return ApiResponse.success(CouponUsageSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(summary = "Get coupon usages by order")
    @GetMapping("/orders/{orderId}/coupon-usages")
    public ApiResponse<PageResponse<CouponUsageResponse>> getUsagesByOrder(
            @Parameter(description = "Order id", required = true)
            @PathVariable UUID orderId,

            @Parameter(description = "Page index, starting from 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponUsageResponse> response =
                couponUsageService.getUsagesByOrder(orderId, page, size);

        return ApiResponse.success(CouponUsageSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(summary = "Get coupon usages by customer")
    @GetMapping("/customers/{customerId}/coupon-usages")
    public ApiResponse<PageResponse<CouponUsageResponse>> getUsagesByCustomer(
            @Parameter(description = "Customer id", required = true)
            @PathVariable UUID customerId,

            @Parameter(description = "Page index, starting from 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponUsageResponse> response =
                couponUsageService.getUsagesByCustomer(customerId, page, size);

        return ApiResponse.success(CouponUsageSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Apply reserved coupon",
            description = "Mark a reserved coupon usage as APPLIED after payment succeeds."
    )
    @PostMapping("/coupon-usages/{usageId}/apply")
    public ApiResponse<CouponUsageResponse> applyUsage(
            @Parameter(description = "Coupon usage id", required = true)
            @PathVariable UUID usageId
    ) {
        CouponUsageResponse response = couponUsageService.applyUsage(usageId);
        return ApiResponse.updated(CouponUsageSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(summary = "Release reserved coupon")
    @PostMapping("/coupon-usages/{usageId}/release")
    public ApiResponse<CouponUsageResponse> releaseUsage(
            @Parameter(description = "Coupon usage id", required = true)
            @PathVariable UUID usageId
    ) {
        CouponUsageResponse response = couponUsageService.releaseUsage(usageId);
        return ApiResponse.updated(CouponUsageSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(summary = "Cancel coupon usage")
    @PostMapping("/coupon-usages/{usageId}/cancel")
    public ApiResponse<CouponUsageResponse> cancelUsage(
            @Parameter(description = "Coupon usage id", required = true)
            @PathVariable UUID usageId
    ) {
        CouponUsageResponse response = couponUsageService.cancelUsage(usageId);
        return ApiResponse.updated(CouponUsageSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(summary = "Expire reserved coupon usage")
    @PostMapping("/coupon-usages/{usageId}/expire")
    public ApiResponse<CouponUsageResponse> expireUsage(
            @Parameter(description = "Coupon usage id", required = true)
            @PathVariable UUID usageId
    ) {
        CouponUsageResponse response = couponUsageService.expireUsage(usageId);
        return ApiResponse.updated(CouponUsageSuccessMessage.STATUS_UPDATED, response);
    }
}
