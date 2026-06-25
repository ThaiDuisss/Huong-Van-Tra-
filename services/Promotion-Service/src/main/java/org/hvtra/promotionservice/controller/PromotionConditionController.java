package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.promotionservice.constant.promotioncondition.PromotionConditionSuccessMessage;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionConditionResponse;
import org.hvtra.promotionservice.service.PromotionConditionService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/promotions/{promotionId}/conditions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý điều kiện khuyến mãi",
        description = "Nhóm API dùng để quản lý điều kiện áp dụng của một khuyến mãi"
)
public class PromotionConditionController {

    PromotionConditionService promotionConditionService;

    @Operation(
            summary = "Tạo điều kiện khuyến mãi",
            description = "Tạo mới một điều kiện áp dụng cho khuyến mãi."
    )
    @Parameter(
            name = "promotionId",
            description = "ID của khuyến mãi",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    @PostMapping
    public ApiResponse<PromotionConditionResponse> createCondition(
            @PathVariable UUID promotionId,

            @Valid @RequestBody PromotionConditionCreateRequest request
    ) {
        return ApiResponse.created(
                PromotionConditionSuccessMessage.CREATED,
                promotionConditionService.createCondition(promotionId, request)
        );
    }

    @Operation(
            summary = "Lấy danh sách điều kiện khuyến mãi",
            description = "Lấy tất cả điều kiện chưa bị xóa của một khuyến mãi, có phân trang."
    )
    @GetMapping
    public ApiResponse<PageResponse<PromotionConditionResponse>> getConditionsByPromotion(
            @Parameter(
                    description = "ID của khuyến mãi",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PromotionConditionResponse> response =
                promotionConditionService.getConditionsByPromotion(promotionId, page, size);

        return ApiResponse.success(
                PromotionConditionSuccessMessage.LIST_FETCHED,
                response
        );
    }

    @Operation(
            summary = "Cập nhật điều kiện khuyến mãi",
            description = "Cập nhật thông tin một điều kiện áp dụng của khuyến mãi."
    )
    @Parameter(
            name = "promotionId",
            description = "ID của khuyến mãi",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    @Parameter(
            name = "conditionId",
            description = "ID của điều kiện khuyến mãi",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440001"
    )
    @PutMapping("/{conditionId}")
    public ApiResponse<PromotionConditionResponse> updateCondition(
            @PathVariable UUID promotionId,
            @PathVariable UUID conditionId,

            @Valid @RequestBody PromotionConditionUpdateRequest request
    ) {
        return ApiResponse.updated(
                PromotionConditionSuccessMessage.UPDATED,
                promotionConditionService.updateCondition(promotionId, conditionId, request)
        );
    }

    @Operation(
            summary = "Xóa điều kiện khuyến mãi",
            description = "Xóa mềm một điều kiện áp dụng của khuyến mãi."
    )
    @Parameter(
            name = "promotionId",
            description = "ID của khuyến mãi",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    @Parameter(
            name = "conditionId",
            description = "ID của điều kiện khuyến mãi",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440001"
    )
    @DeleteMapping("/{conditionId}")
    public ApiResponse<Void> deleteCondition(
            @PathVariable UUID promotionId,
            @PathVariable UUID conditionId
    ) {
        promotionConditionService.deleteCondition(promotionId, conditionId);

        return ApiResponse.deleted(PromotionConditionSuccessMessage.DELETED);
    }
}
