package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.promotionservice.constant.coupon.CouponSuccessMessage;
import org.hvtra.promotionservice.dto.request.coupon.CouponCreateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponStatusUpdateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponUpdateRequest;
import org.hvtra.promotionservice.dto.response.CouponResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.service.CouponService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý mã giảm giá",
        description = "Nhóm API dùng để quản lý mã coupon của khuyến mãi"
)
public class CouponController {

    CouponService couponService;

    @Operation(
            summary = "Tạo mã giảm giá",
            description = "Tạo mới một mã giảm giá cho khuyến mãi."
    )
    @Parameter(
            name = "promotionId",
            description = "ID của khuyến mãi",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    @PostMapping("/api/promotions/{promotionId}/coupons")
    public ApiResponse<CouponResponse> createCoupon(
            @PathVariable UUID promotionId,
            @Valid @RequestBody CouponCreateRequest request
    ) {
        return ApiResponse.created(
                CouponSuccessMessage.CREATED,
                couponService.createCoupon(promotionId, request)
        );
    }

    @Operation(
            summary = "Lấy danh sách mã giảm giá của khuyến mãi",
            description = "Lấy danh sách coupon thuộc một khuyến mãi, có phân trang."
    )
    @Parameter(
            name = "promotionId",
            description = "ID của khuyến mãi",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    @GetMapping("/api/promotions/{promotionId}/coupons")
    public ApiResponse<PageResponse<CouponResponse>> getCouponsByPromotion(
            @PathVariable UUID promotionId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(
                CouponSuccessMessage.LIST_FETCHED,
                couponService.getCouponsByPromotion(promotionId, page, size)
        );
    }

    @Operation(
            summary = "Lấy chi tiết mã giảm giá",
            description = "Lấy thông tin chi tiết của một coupon theo ID."
    )
    @Parameter(
            name = "couponId",
            description = "ID của coupon",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440001"
    )
    @GetMapping("/api/coupons/{couponId}")
    public ApiResponse<CouponResponse> getCouponById(
            @PathVariable UUID couponId
    ) {
        return ApiResponse.success(
                CouponSuccessMessage.FETCHED,
                couponService.getCouponById(couponId)
        );
    }

    @Operation(
            summary = "Cập nhật mã giảm giá",
            description = "Cập nhật thông tin của một coupon."
    )
    @Parameter(
            name = "couponId",
            description = "ID của coupon",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440001"
    )
    @PutMapping("/api/coupons/{couponId}")
    public ApiResponse<CouponResponse> updateCoupon(
            @PathVariable UUID couponId,
            @Valid @RequestBody CouponUpdateRequest request
    ) {
        return ApiResponse.updated(
                CouponSuccessMessage.UPDATED,
                couponService.updateCoupon(couponId, request)
        );
    }

    @Operation(
            summary = "Cập nhật trạng thái mã giảm giá",
            description = "Thay đổi trạng thái của coupon."
    )
    @Parameter(
            name = "couponId",
            description = "ID của coupon",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440001"
    )
    @PatchMapping("/api/coupons/{couponId}/status")
    public ApiResponse<CouponResponse> updateCouponStatus(
            @PathVariable UUID couponId,
            @Valid @RequestBody CouponStatusUpdateRequest request
    ) {
        return ApiResponse.updated(
                CouponSuccessMessage.STATUS_UPDATED,
                couponService.updateCouponStatus(couponId, request)
        );
    }

    @Operation(
            summary = "Xóa mã giảm giá",
            description = "Xóa mềm một coupon."
    )
    @Parameter(
            name = "couponId",
            description = "ID của coupon",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440001"
    )
    @DeleteMapping("/api/coupons/{couponId}")
    public ApiResponse<Void> deleteCoupon(
            @PathVariable UUID couponId
    ) {
        couponService.deleteCoupon(couponId);

        return ApiResponse.deleted(CouponSuccessMessage.DELETED);
    }
}
