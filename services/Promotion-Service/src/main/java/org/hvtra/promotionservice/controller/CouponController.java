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
import org.hvtra.promotionservice.constant.coupon.CouponSuccessMessage;
import org.hvtra.promotionservice.dto.request.coupon.*;
import org.hvtra.promotionservice.dto.response.CouponResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.service.CouponService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPath.COUPON_USAGES)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý coupon",
        description = "Nhóm API dùng để quản lý mã coupon public hoặc mã cá nhân thuộc chương trình khuyến mãi"
)
public class CouponController {

    CouponService couponService;

    @Operation(
            summary = "Tạo coupon",
            description = "Tạo mới một mã coupon. Khi gọi API này cần truyền promotionId trong request."
    )
    @PostMapping("/coupons")
    public ApiResponse<CouponResponse> createCoupon(
            @Valid @RequestBody CouponCreateRequest request
    ) {
        CouponResponse response = couponService.createCoupon(request);
        return ApiResponse.created(CouponSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Tạo coupon theo khuyến mãi",
            description = "Tạo mới một mã coupon trực tiếp dưới một khuyến mãi cụ thể. promotionId được lấy từ path variable."
    )
    @PostMapping("/promotions/{promotionId}/coupons")
    public ApiResponse<CouponResponse> createCouponByPromotion(
            @Parameter(description = "ID của khuyến mãi chứa coupon", required = true)
            @PathVariable UUID promotionId,

            @Valid @RequestBody CouponCreateRequest request
    ) {
        CouponResponse response = couponService.createCouponByPromotion(promotionId, request);
        return ApiResponse.created(CouponSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Lấy chi tiết coupon",
            description = "Lấy thông tin chi tiết của một coupon theo ID."
    )
    @GetMapping("/coupons/{couponId}")
    public ApiResponse<CouponResponse> getCouponById(
            @Parameter(description = "ID của coupon cần lấy thông tin", required = true)
            @PathVariable UUID couponId
    ) {
        CouponResponse response = couponService.getCouponById(couponId);
        return ApiResponse.success(CouponSuccessMessage.FETCHED, response);
    }

    @Operation(
            summary = "Lấy danh sách coupon",
            description = "Lấy danh sách coupon chưa bị xóa mềm, có hỗ trợ phân trang."
    )
    @GetMapping("/coupons")
    public ApiResponse<PageResponse<CouponResponse>> getAllCoupons(
            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponResponse> response = couponService.getAllCoupons(page, size);
        return ApiResponse.success(CouponSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Lấy danh sách coupon theo khuyến mãi",
            description = "Lấy danh sách coupon thuộc một khuyến mãi cụ thể, có hỗ trợ phân trang."
    )
    @GetMapping("/promotions/{promotionId}/coupons")
    public ApiResponse<PageResponse<CouponResponse>> getCouponsByPromotion(
            @Parameter(description = "ID của khuyến mãi cần lấy coupon", required = true)
            @PathVariable UUID promotionId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponResponse> response =
                couponService.getCouponsByPromotion(promotionId, page, size);

        return ApiResponse.success(CouponSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Cập nhật coupon",
            description = "Cập nhật thông tin coupon như mã, phạm vi sử dụng, khách hàng được gán, số lần sử dụng và thời gian hết hạn."
    )
    @PutMapping("/coupons/{couponId}")
    public ApiResponse<CouponResponse> updateCoupon(
            @Parameter(description = "ID của coupon cần cập nhật", required = true)
            @PathVariable UUID couponId,

            @Valid @RequestBody CouponUpdateRequest request
    ) {
        CouponResponse response = couponService.updateCoupon(couponId, request);
        return ApiResponse.updated(CouponSuccessMessage.UPDATED, response);
    }

    @Operation(
            summary = "Cập nhật trạng thái coupon",
            description = "Cập nhật trạng thái vòng đời của coupon. Ví dụ: ACTIVE, PAUSED, EXPIRED, CANCELLED hoặc ARCHIVED."
    )
    @PatchMapping("/coupons/{couponId}/status")
    public ApiResponse<CouponResponse> updateCouponStatus(
            @Parameter(description = "ID của coupon cần cập nhật trạng thái", required = true)
            @PathVariable UUID couponId,

            @Valid @RequestBody CouponStatusUpdateRequest request
    ) {
        CouponResponse response = couponService.updateCouponStatus(couponId, request);
        return ApiResponse.updated(CouponSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Xóa coupon",
            description = "Xóa mềm coupon. Dữ liệu không bị xóa vật lý khỏi database."
    )
    @DeleteMapping("/coupons/{couponId}")
    public ApiResponse<Void> deleteCoupon(
            @Parameter(description = "ID của coupon cần xóa", required = true)
            @PathVariable UUID couponId
    ) {
        couponService.deleteCoupon(couponId);
        return ApiResponse.deleted(CouponSuccessMessage.DELETED);
    }

    @Operation(
            summary = "Kích hoạt coupon",
            description = "Chuyển coupon sang trạng thái ACTIVE để có thể sử dụng."
    )
    @PostMapping("/coupons/{couponId}/activate")
    public ApiResponse<CouponResponse> activateCoupon(
            @Parameter(description = "ID của coupon cần kích hoạt", required = true)
            @PathVariable UUID couponId
    ) {
        CouponResponse response = couponService.activateCoupon(couponId);
        return ApiResponse.updated(CouponSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Tạm dừng coupon",
            description = "Chuyển coupon sang trạng thái PAUSED. Coupon tạm dừng sẽ không được sử dụng cho đến khi kích hoạt lại."
    )
    @PostMapping("/coupons/{couponId}/pause")
    public ApiResponse<CouponResponse> pauseCoupon(
            @Parameter(description = "ID của coupon cần tạm dừng", required = true)
            @PathVariable UUID couponId
    ) {
        CouponResponse response = couponService.pauseCoupon(couponId);
        return ApiResponse.updated(CouponSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Hủy coupon",
            description = "Chuyển coupon sang trạng thái CANCELLED. Coupon đã hủy không nên được kích hoạt lại."
    )
    @PostMapping("/coupons/{couponId}/cancel")
    public ApiResponse<CouponResponse> cancelCoupon(
            @Parameter(description = "ID của coupon cần hủy", required = true)
            @PathVariable UUID couponId
    ) {
        CouponResponse response = couponService.cancelCoupon(couponId);
        return ApiResponse.updated(CouponSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Lưu trữ coupon",
            description = "Chuyển coupon sang trạng thái ARCHIVED. Dùng để ẩn coupon khỏi danh sách quản lý chính nhưng vẫn giữ lịch sử."
    )
    @PostMapping("/coupons/{couponId}/archive")
    public ApiResponse<CouponResponse> archiveCoupon(
            @Parameter(description = "ID của coupon cần lưu trữ", required = true)
            @PathVariable UUID couponId
    ) {
        CouponResponse response = couponService.archiveCoupon(couponId);
        return ApiResponse.updated(CouponSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Tạo mã coupon public",
            description = "Tạo một mã coupon public cho một khuyến mãi. Mã public có thể được dùng bởi bất kỳ khách hàng nào nếu thỏa điều kiện."
    )
    @PostMapping("/promotions/{promotionId}/coupons/generate-public")
    public ApiResponse<CouponResponse> generatePublicCoupon(
            @Parameter(description = "ID của khuyến mãi cần tạo coupon", required = true)
            @PathVariable UUID promotionId,

            @Valid @RequestBody CouponGeneratePublicRequest request
    ) {
        CouponResponse response = couponService.generatePublicCoupon(promotionId, request);
        return ApiResponse.created(CouponSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Tạo mã coupon cá nhân",
            description = "Tạo một mã coupon cá nhân cho một khách hàng cụ thể."
    )
    @PostMapping("/promotions/{promotionId}/coupons/generate-personal")
    public ApiResponse<CouponResponse> generatePersonalCoupon(
            @Parameter(description = "ID của khuyến mãi cần tạo coupon", required = true)
            @PathVariable UUID promotionId,

            @Valid @RequestBody CouponGeneratePersonalRequest request
    ) {
        CouponResponse response = couponService.generatePersonalCoupon(promotionId, request);
        return ApiResponse.created(CouponSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Tạo hàng loạt coupon",
            description = "Tạo hàng loạt coupon public hoặc coupon cá nhân cho một khuyến mãi."
    )
    @PostMapping("/promotions/{promotionId}/coupons/bulk-generate")
    public ApiResponse<List<CouponResponse>> bulkGenerateCoupons(
            @Parameter(description = "ID của khuyến mãi cần tạo coupon", required = true)
            @PathVariable UUID promotionId,

            @Valid @RequestBody CouponBulkGenerateRequest request
    ) {
        List<CouponResponse> response = couponService.bulkGenerateCoupons(promotionId, request);
        return ApiResponse.created(CouponSuccessMessage.CREATED, response);
    }
}