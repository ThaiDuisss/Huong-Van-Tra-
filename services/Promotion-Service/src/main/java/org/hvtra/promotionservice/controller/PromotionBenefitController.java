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
import org.hvtra.promotionservice.constant.promotionbenefit.PromotionBenefitSuccessMessage;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitCreateRequest;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionBenefitResponse;
import org.hvtra.promotionservice.service.PromotionBenefitService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPath.PROMOTION_SERVICE + "/{promotionId}/benefits")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý quyền lợi khuyến mãi",
        description = "Nhóm API dùng để quản lý quyền lợi/hành động giảm giá của một chương trình khuyến mãi"
)
public class PromotionBenefitController {

    PromotionBenefitService promotionBenefitService;

    @Operation(
            summary = "Tạo quyền lợi khuyến mãi",
            description = "Tạo mới quyền lợi cho khuyến mãi. Ví dụ: giảm phần trăm, giảm tiền cố định, miễn phí vận chuyển, tặng sản phẩm hoặc mua X tặng Y."
    )
    @PostMapping
    public ApiResponse<PromotionBenefitResponse> createBenefit(
            @Parameter(
                    description = "ID của khuyến mãi cần thêm quyền lợi",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Valid @RequestBody PromotionBenefitCreateRequest request
    ) {
        PromotionBenefitResponse response =
                promotionBenefitService.createBenefit(promotionId, request);

        return ApiResponse.created(PromotionBenefitSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Lấy danh sách quyền lợi khuyến mãi",
            description = "Lấy danh sách các quyền lợi chưa bị xóa mềm của một khuyến mãi, có hỗ trợ phân trang."
    )
    @GetMapping
    public ApiResponse<PageResponse<PromotionBenefitResponse>> getBenefitsByPromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần lấy danh sách quyền lợi",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PromotionBenefitResponse> response =
                promotionBenefitService.getBenefitsByPromotion(promotionId, page, size);

        return ApiResponse.success(PromotionBenefitSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Lấy chi tiết quyền lợi khuyến mãi",
            description = "Lấy thông tin chi tiết của một quyền lợi/hành động giảm giá theo ID."
    )
    @GetMapping("/{benefitId}")
    public ApiResponse<PromotionBenefitResponse> getBenefitById(
            @Parameter(
                    description = "ID của khuyến mãi chứa quyền lợi",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Parameter(
                    description = "ID của quyền lợi cần lấy thông tin",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440001"
            )
            @PathVariable UUID benefitId
    ) {
        PromotionBenefitResponse response =
                promotionBenefitService.getBenefitById(promotionId, benefitId);

        return ApiResponse.success(PromotionBenefitSuccessMessage.FETCHED, response);
    }

    @Operation(
            summary = "Cập nhật quyền lợi khuyến mãi",
            description = "Cập nhật cấu hình quyền lợi/hành động giảm giá của khuyến mãi."
    )
    @PutMapping("/{benefitId}")
    public ApiResponse<PromotionBenefitResponse> updateBenefit(
            @Parameter(
                    description = "ID của khuyến mãi chứa quyền lợi",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Parameter(
                    description = "ID của quyền lợi cần cập nhật",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440001"
            )
            @PathVariable UUID benefitId,

            @Valid @RequestBody PromotionBenefitUpdateRequest request
    ) {
        PromotionBenefitResponse response =
                promotionBenefitService.updateBenefit(promotionId, benefitId, request);

        return ApiResponse.updated(PromotionBenefitSuccessMessage.UPDATED, response);
    }

    @Operation(
            summary = "Xóa quyền lợi khuyến mãi",
            description = "Xóa mềm một quyền lợi/hành động giảm giá của khuyến mãi. Dữ liệu không bị xóa vật lý khỏi database."
    )
    @DeleteMapping("/{benefitId}")
    public ApiResponse<Void> deleteBenefit(
            @Parameter(
                    description = "ID của khuyến mãi chứa quyền lợi",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Parameter(
                    description = "ID của quyền lợi cần xóa",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440001"
            )
            @PathVariable UUID benefitId
    ) {
        promotionBenefitService.deleteBenefit(promotionId, benefitId);

        return ApiResponse.deleted(PromotionBenefitSuccessMessage.DELETED);
    }
}
