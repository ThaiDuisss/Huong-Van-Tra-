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
//import org.hvtra.promotionservice.constant.promotion.PromotionSuccessMessage;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionStatusUpdateRequest;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.dto.response.PromotionResponse;
//import org.hvtra.promotionservice.service.PromotionService;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.UUID;
//
//@RestController
//@RequestMapping("/api/promotions")
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//@Tag(
//        name = "Quản lý mã khuyến mãi",
//        description = "Nhóm API dùng để quản lý các mã giảm giá thuộc sự kiện khuyến mãi trong hệ thống Hương Vân Trà"
//)
//public class PromotionController {
//
//    PromotionService promotionService;
//
//    @Operation(
//            summary = "Tạo khuyến mãi",
//            description = "Tạo mới một khuyến mãi thuộc một sự kiện khuyến mãi."
//    )
//    @PostMapping
//    public ApiResponse<PromotionResponse> createPromotion(
//            @Valid @RequestBody PromotionCreateRequest request
//    ) {
//        return ApiResponse.created(
//                PromotionSuccessMessage.CREATED,
//                promotionService.createPromotion(request)
//        );
//    }
//
//    @Operation(
//            summary = "Lấy chi tiết khuyến mãi",
//            description = "Lấy thông tin chi tiết của một khuyến mãi theo ID."
//    )
//    @Parameter(
//            name = "promotionId",
//            description = "ID của khuyến mãi cần lấy thông tin",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @GetMapping("/{promotionId}")
//    public ApiResponse<PromotionResponse> getPromotionById(
//            @PathVariable UUID promotionId
//    ) {
//        return ApiResponse.success(
//                PromotionSuccessMessage.FETCHED,
//                promotionService.getPromotionById(promotionId)
//        );
//    }
//
//    @Operation(
//            summary = "Lấy danh sách khuyến mãi",
//            description = "Lấy danh sách tất cả khuyến mãi chưa bị xóa, có phân trang."
//    )
//    @GetMapping
//    public ApiResponse<PageResponse<PromotionResponse>> getAllPromotions(
//            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
//            @RequestParam(defaultValue = "0") int page,
//
//            @Parameter(description = "Số lượng phần tử mỗi trang", example = "10")
//            @RequestParam(defaultValue = "10") int size
//    ) {
//        return ApiResponse.success(
//                PromotionSuccessMessage.LIST_FETCHED,
//                promotionService.getAllPromotions(page, size)
//        );
//    }
//
//    @Operation(
//            summary = "Cập nhật khuyến mãi",
//            description = "Cập nhật thông tin cơ bản của khuyến mãi như tên, thời gian áp dụng và thông tin cấu hình khuyến mãi."
//    )
//    @Parameter(
//            name = "promotionId",
//            description = "ID của khuyến mãi cần cập nhật",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @PutMapping("/{promotionId}")
//    public ApiResponse<PromotionResponse> updatePromotion(
//            @PathVariable UUID promotionId,
//
//            @Valid @RequestBody PromotionUpdateRequest request
//    ) {
//        return ApiResponse.updated(
//                PromotionSuccessMessage.UPDATED,
//                promotionService.updatePromotion(promotionId, request)
//        );
//    }
//
//    @Operation(
//            summary = "Cập nhật trạng thái khuyến mãi",
//            description = "Thay đổi trạng thái của khuyến mãi. Ví dụ: ACTIVE, INACTIVE, EXPIRED hoặc CANCELLED."
//    )
//    @Parameter(
//            name = "promotionId",
//            description = "ID của khuyến mãi cần cập nhật trạng thái",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @PatchMapping("/{promotionId}/status")
//    public ApiResponse<PromotionResponse> updatePromotionStatus(
//            @PathVariable UUID promotionId,
//
//            @Valid @RequestBody PromotionStatusUpdateRequest request
//    ) {
//        return ApiResponse.updated(
//                PromotionSuccessMessage.STATUS_UPDATED,
//                promotionService.updatePromotionStatus(promotionId, request)
//        );
//    }
//
//    @Operation(
//            summary = "Xóa khuyến mãi",
//            description = "Xóa mềm một khuyến mãi. Dữ liệu không bị xóa vật lý khỏi database mà chỉ được đánh dấu là đã xóa."
//    )
//    @Parameter(
//            name = "promotionId",
//            description = "ID của khuyến mãi cần xóa",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @DeleteMapping("/{promotionId}")
//    public ApiResponse<Void> deletePromotion(
//            @PathVariable UUID promotionId
//    ) {
//        promotionService.deletePromotion(promotionId);
//
//        return ApiResponse.deleted(PromotionSuccessMessage.DELETED);
//    }
//}