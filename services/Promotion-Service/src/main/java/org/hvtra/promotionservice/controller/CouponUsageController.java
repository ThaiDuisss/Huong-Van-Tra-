package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.promotionservice.constant.ApiPath;
import org.hvtra.promotionservice.constant.couponusage.CouponUsageSuccessMessage;
import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.service.CouponUsageService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPath.COUPON_USAGES)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý lịch sử sử dụng coupon",
        description = "Nhóm API dùng để tra cứu và xử lý trạng thái các phiên sử dụng/giữ chỗ coupon"
)
public class CouponUsageController {

    CouponUsageService couponUsageService;

    @Operation(
            summary = "Lấy danh sách lịch sử sử dụng coupon",
            description = "Lấy danh sách tất cả phiên sử dụng coupon chưa bị xóa mềm, có hỗ trợ phân trang."
    )
    @GetMapping("/coupon-usages")
    public ApiResponse<PageResponse<CouponUsageResponse>> getAllCouponUsages(
            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponUsageResponse> response =
                couponUsageService.getAllCouponUsages(page, size);

        return ApiResponse.success(CouponUsageSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Lấy chi tiết phiên sử dụng coupon",
            description = "Lấy thông tin chi tiết của một phiên sử dụng/giữ chỗ coupon theo ID."
    )
    @GetMapping("/coupon-usages/{usageId}")
    public ApiResponse<CouponUsageResponse> getCouponUsageById(
            @Parameter(description = "ID phiên sử dụng coupon", required = true)
            @PathVariable UUID usageId
    ) {
        CouponUsageResponse response = couponUsageService.getCouponUsageById(usageId);
        return ApiResponse.success(CouponUsageSuccessMessage.FETCHED, response);
    }

    @Operation(
            summary = "Lấy lịch sử sử dụng theo coupon",
            description = "Lấy danh sách các phiên sử dụng/giữ chỗ của một coupon cụ thể."
    )
    @GetMapping("/coupons/{couponId}/usages")
    public ApiResponse<PageResponse<CouponUsageResponse>> getUsagesByCoupon(
            @Parameter(description = "ID coupon", required = true)
            @PathVariable UUID couponId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponUsageResponse> response =
                couponUsageService.getUsagesByCoupon(couponId, page, size);

        return ApiResponse.success(CouponUsageSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Lấy lịch sử sử dụng coupon theo đơn hàng",
            description = "Lấy danh sách các coupon đã được giữ, áp dụng hoặc nhả theo một đơn hàng cụ thể."
    )
    @GetMapping("/orders/{orderId}/coupon-usages")
    public ApiResponse<PageResponse<CouponUsageResponse>> getUsagesByOrder(
            @Parameter(description = "ID đơn hàng", required = true)
            @PathVariable UUID orderId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponUsageResponse> response =
                couponUsageService.getUsagesByOrder(orderId, page, size);

        return ApiResponse.success(CouponUsageSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Lấy lịch sử sử dụng coupon theo khách hàng",
            description = "Lấy danh sách các phiên sử dụng/giữ chỗ coupon của một khách hàng cụ thể."
    )
    @GetMapping("/customers/{customerId}/coupon-usages")
    public ApiResponse<PageResponse<CouponUsageResponse>> getUsagesByCustomer(
            @Parameter(description = "ID khách hàng", required = true)
            @PathVariable UUID customerId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CouponUsageResponse> response =
                couponUsageService.getUsagesByCustomer(customerId, page, size);

        return ApiResponse.success(CouponUsageSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Nhả coupon đã giữ",
            description = "Chuyển phiên sử dụng coupon sang RELEASED. Dùng khi đơn hàng bị hủy hoặc thanh toán thất bại."
    )
    @PostMapping("/coupon-usages/{usageId}/release")
    public ApiResponse<CouponUsageResponse> releaseUsage(
            @Parameter(description = "ID phiên sử dụng coupon cần nhả", required = true)
            @PathVariable UUID usageId
    ) {
        CouponUsageResponse response = couponUsageService.releaseUsage(usageId);
        return ApiResponse.updated(CouponUsageSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Hủy phiên sử dụng coupon",
            description = "Chuyển phiên sử dụng coupon sang CANCELLED. Dùng cho trường hợp cần hủy thủ công một phiên sử dụng."
    )
    @PostMapping("/coupon-usages/{usageId}/cancel")
    public ApiResponse<CouponUsageResponse> cancelUsage(
            @Parameter(description = "ID phiên sử dụng coupon cần hủy", required = true)
            @PathVariable UUID usageId
    ) {
        CouponUsageResponse response = couponUsageService.cancelUsage(usageId);
        return ApiResponse.updated(CouponUsageSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Đánh dấu phiên giữ coupon hết hạn",
            description = "Chuyển phiên sử dụng coupon sang EXPIRED. Dùng khi reservation đã quá thời gian cho phép."
    )
    @PostMapping("/coupon-usages/{usageId}/expire")
    public ApiResponse<CouponUsageResponse> expireUsage(
            @Parameter(description = "ID phiên sử dụng coupon cần đánh dấu hết hạn", required = true)
            @PathVariable UUID usageId
    ) {
        CouponUsageResponse response = couponUsageService.expireUsage(usageId);
        return ApiResponse.updated(CouponUsageSuccessMessage.STATUS_UPDATED, response);
    }
}