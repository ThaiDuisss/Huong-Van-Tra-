//package org.hvtra.promotionservice.controller;
//
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.Parameter;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.validation.Valid;
//import lombok.AccessLevel;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import org.hvtra.commonservice.domain.response.ApiResponse;
//import org.hvtra.promotionservice.constant.couponusage.CouponUsageSuccessMessage;
//import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
//import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.service.CouponUsageService;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.UUID;
//
//@RestController
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//@Tag(
//        name = "Quản lý lịch sử sử dụng coupon",
//        description = "Nhóm API dùng để giữ chỗ, xác nhận, hoàn mã và xem lịch sử sử dụng coupon"
//)
//public class CouponUsageController {
//
//    CouponUsageService couponUsageService;
//
//    @Operation(
//            summary = "Giữ chỗ coupon",
//            description = "Giữ chỗ coupon cho một đơn hàng trong quá trình checkout."
//    )
//    @PostMapping("/api/coupon-usages/reserve")
//    public ApiResponse<CouponUsageResponse> reserveCoupon(
//            @Valid @RequestBody CouponUsageReserveRequest request
//    ) {
//        return ApiResponse.created(
//                CouponUsageSuccessMessage.RESERVED,
//                couponUsageService.reserveCoupon(request)
//        );
//    }
//
//    @Operation(
//            summary = "Xác nhận sử dụng coupon",
//            description = "Xác nhận coupon đã được sử dụng sau khi đơn hàng hoặc thanh toán thành công."
//    )
//    @Parameter(
//            name = "usageId",
//            description = "ID lịch sử sử dụng coupon",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @PatchMapping("/api/coupon-usages/{usageId}/confirm")
//    public ApiResponse<CouponUsageResponse> confirmCouponUsage(
//            @PathVariable UUID usageId
//    ) {
//        return ApiResponse.updated(
//                CouponUsageSuccessMessage.CONFIRMED,
//                couponUsageService.confirmCouponUsage(usageId)
//        );
//    }
//
//    @Operation(
//            summary = "Hoàn coupon đã giữ chỗ",
//            description = "Hoàn lại coupon đã giữ chỗ khi đơn hàng bị hủy hoặc thanh toán thất bại."
//    )
//    @Parameter(
//            name = "usageId",
//            description = "ID lịch sử sử dụng coupon",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @PatchMapping("/api/coupon-usages/{usageId}/release")
//    public ApiResponse<CouponUsageResponse> releaseCouponUsage(
//            @PathVariable UUID usageId
//    ) {
//        return ApiResponse.updated(
//                CouponUsageSuccessMessage.RELEASED,
//                couponUsageService.releaseCouponUsage(usageId)
//        );
//    }
//
//    @Operation(
//            summary = "Lấy chi tiết lịch sử sử dụng coupon",
//            description = "Lấy thông tin chi tiết của một bản ghi sử dụng coupon."
//    )
//    @Parameter(
//            name = "usageId",
//            description = "ID lịch sử sử dụng coupon",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @GetMapping("/api/coupon-usages/{usageId}")
//    public ApiResponse<CouponUsageResponse> getCouponUsageById(
//            @PathVariable UUID usageId
//    ) {
//        return ApiResponse.success(
//                CouponUsageSuccessMessage.FETCHED,
//                couponUsageService.getCouponUsageById(usageId)
//        );
//    }
//
//    @Operation(
//            summary = "Lấy lịch sử sử dụng theo coupon",
//            description = "Lấy danh sách lịch sử sử dụng của một coupon, có phân trang."
//    )
//    @Parameter(
//            name = "couponId",
//            description = "ID coupon",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440001"
//    )
//    @GetMapping("/api/coupons/{couponId}/usages")
//    public ApiResponse<PageResponse<CouponUsageResponse>> getCouponUsagesByCoupon(
//            @PathVariable UUID couponId,
//
//            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
//            @RequestParam(defaultValue = "0") int page,
//
//            @Parameter(description = "Số lượng phần tử mỗi trang", example = "10")
//            @RequestParam(defaultValue = "10") int size
//    ) {
//        return ApiResponse.success(
//                CouponUsageSuccessMessage.LIST_FETCHED,
//                couponUsageService.getCouponUsagesByCoupon(couponId, page, size)
//        );
//    }
//
//    @Operation(
//            summary = "Lấy lịch sử sử dụng coupon theo khách hàng",
//            description = "Lấy danh sách lịch sử sử dụng coupon của một khách hàng, có phân trang."
//    )
//    @Parameter(
//            name = "customerId",
//            description = "ID khách hàng",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440002"
//    )
//    @GetMapping("/api/customers/{customerId}/coupon-usages")
//    public ApiResponse<PageResponse<CouponUsageResponse>> getCouponUsagesByCustomer(
//            @PathVariable UUID customerId,
//
//            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
//            @RequestParam(defaultValue = "0") int page,
//
//            @Parameter(description = "Số lượng phần tử mỗi trang", example = "10")
//            @RequestParam(defaultValue = "10") int size
//    ) {
//        return ApiResponse.success(
//                CouponUsageSuccessMessage.LIST_FETCHED,
//                couponUsageService.getCouponUsagesByCustomer(customerId, page, size)
//        );
//    }
//}
