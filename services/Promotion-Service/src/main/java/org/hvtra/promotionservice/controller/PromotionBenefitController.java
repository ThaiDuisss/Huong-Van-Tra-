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
//import org.hvtra.promotionservice.constant.promotionbenefit.PromotionBenefitSuccessMessage;
//import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitCreateRequest;
//import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitUpdateRequest;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.dto.response.PromotionBenefitResponse;
//import org.hvtra.promotionservice.service.PromotionBenefitService;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.UUID;
//
//@RestController
//@RequestMapping("/api/promotions/{promotionId}/benefits")
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//@Tag(
//        name = "Quản lý quyền lợi khuyến mãi",
//        description = "Nhóm API dùng để quản lý quyền lợi hoặc giá trị giảm của một khuyến mãi"
//)
//public class PromotionBenefitController {
//
//    PromotionBenefitService promotionBenefitService;
//
//    @Operation(
//            summary = "Tạo quyền lợi khuyến mãi",
//            description = "Tạo mới một quyền lợi áp dụng cho khuyến mãi."
//    )
//    @Parameter(
//            name = "promotionId",
//            description = "ID của khuyến mãi",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @PostMapping
//    public ApiResponse<PromotionBenefitResponse> createBenefit(
//            @PathVariable UUID promotionId,
//
//            @Valid @RequestBody PromotionBenefitCreateRequest request
//    ) {
//        return ApiResponse.created(
//                PromotionBenefitSuccessMessage.CREATED,
//                promotionBenefitService.createBenefit(promotionId, request)
//        );
//    }
//
//    @Operation(
//            summary = "Lấy danh sách quyền lợi khuyến mãi",
//            description = "Lấy tất cả quyền lợi chưa bị xóa của một khuyến mãi, có phân trang."
//    )
//    @Parameter(
//            name = "promotionId",
//            description = "ID của khuyến mãi",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @GetMapping
//    public ApiResponse<PageResponse<PromotionBenefitResponse>> getBenefitsByPromotion(
//            @PathVariable UUID promotionId,
//
//            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
//            @RequestParam(defaultValue = "0") int page,
//
//            @Parameter(description = "Số lượng phần tử mỗi trang", example = "10")
//            @RequestParam(defaultValue = "10") int size
//    ) {
//        return ApiResponse.success(
//                PromotionBenefitSuccessMessage.LIST_FETCHED,
//                promotionBenefitService.getBenefitsByPromotion(promotionId, page, size)
//        );
//    }
//
//    @Operation(
//            summary = "Cập nhật quyền lợi khuyến mãi",
//            description = "Cập nhật thông tin một quyền lợi áp dụng của khuyến mãi."
//    )
//    @Parameter(
//            name = "promotionId",
//            description = "ID của khuyến mãi",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @Parameter(
//            name = "benefitId",
//            description = "ID của quyền lợi khuyến mãi",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440001"
//    )
//    @PutMapping("/{benefitId}")
//    public ApiResponse<PromotionBenefitResponse> updateBenefit(
//            @PathVariable UUID promotionId,
//            @PathVariable UUID benefitId,
//
//            @Valid @RequestBody PromotionBenefitUpdateRequest request
//    ) {
//        return ApiResponse.updated(
//                PromotionBenefitSuccessMessage.UPDATED,
//                promotionBenefitService.updateBenefit(promotionId, benefitId, request)
//        );
//    }
//
//    @Operation(
//            summary = "Xóa quyền lợi khuyến mãi",
//            description = "Xóa mềm một quyền lợi áp dụng của khuyến mãi."
//    )
//    @Parameter(
//            name = "promotionId",
//            description = "ID của khuyến mãi",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440000"
//    )
//    @Parameter(
//            name = "benefitId",
//            description = "ID của quyền lợi khuyến mãi",
//            required = true,
//            example = "550e8400-e29b-41d4-a716-446655440001"
//    )
//    @DeleteMapping("/{benefitId}")
//    public ApiResponse<Void> deleteBenefit(
//            @PathVariable UUID promotionId,
//            @PathVariable UUID benefitId
//    ) {
//        promotionBenefitService.deleteBenefit(promotionId, benefitId);
//
//        return ApiResponse.deleted(PromotionBenefitSuccessMessage.DELETED);
//    }
//}
