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
@RequestMapping("/api/v1/promotions/{promotionId}/conditions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý điều kiện khuyến mãi",
        description = "Nhóm API dùng để quản lý các điều kiện áp dụng của một chương trình khuyến mãi"
)
public class PromotionConditionController {

    PromotionConditionService promotionConditionService;

    @Operation(
            summary = "Tạo điều kiện khuyến mãi",
            description = "Tạo mới một điều kiện áp dụng cho khuyến mãi. Ví dụ: đơn hàng tối thiểu 500.000đ, khách VIP, kênh POS hoặc phương thức thanh toán BANK_TRANSFER."
    )
    @PostMapping
    public ApiResponse<PromotionConditionResponse> createCondition(
            @Parameter(
                    description = "ID của khuyến mãi cần thêm điều kiện",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Valid @RequestBody PromotionConditionCreateRequest request
    ) {
        PromotionConditionResponse response =
                promotionConditionService.createCondition(promotionId, request);

        return ApiResponse.created(PromotionConditionSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Lấy danh sách điều kiện khuyến mãi",
            description = "Lấy danh sách các điều kiện chưa bị xóa mềm của một khuyến mãi, có hỗ trợ phân trang."
    )
    @GetMapping
    public ApiResponse<PageResponse<PromotionConditionResponse>> getConditionsByPromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần lấy danh sách điều kiện",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PromotionConditionResponse> response =
                promotionConditionService.getConditionsByPromotion(promotionId, page, size);

        return ApiResponse.success(PromotionConditionSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Lấy chi tiết điều kiện khuyến mãi",
            description = "Lấy thông tin chi tiết của một điều kiện áp dụng khuyến mãi theo ID."
    )
    @GetMapping("/{conditionId}")
    public ApiResponse<PromotionConditionResponse> getConditionById(
            @Parameter(
                    description = "ID của khuyến mãi chứa điều kiện",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Parameter(
                    description = "ID của điều kiện cần lấy thông tin",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440001"
            )
            @PathVariable UUID conditionId
    ) {
        PromotionConditionResponse response =
                promotionConditionService.getConditionById(promotionId, conditionId);

        return ApiResponse.success(PromotionConditionSuccessMessage.FETCHED, response);
    }

    @Operation(
            summary = "Cập nhật điều kiện khuyến mãi",
            description = "Cập nhật loại điều kiện, toán tử so sánh và giá trị JSON của một điều kiện áp dụng khuyến mãi."
    )
    @PutMapping("/{conditionId}")
    public ApiResponse<PromotionConditionResponse> updateCondition(
            @Parameter(
                    description = "ID của khuyến mãi chứa điều kiện",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Parameter(
                    description = "ID của điều kiện cần cập nhật",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440001"
            )
            @PathVariable UUID conditionId,

            @Valid @RequestBody PromotionConditionUpdateRequest request
    ) {
        PromotionConditionResponse response =
                promotionConditionService.updateCondition(promotionId, conditionId, request);

        return ApiResponse.updated(PromotionConditionSuccessMessage.UPDATED, response);
    }

    @Operation(
            summary = "Xóa điều kiện khuyến mãi",
            description = "Xóa mềm một điều kiện áp dụng của khuyến mãi. Dữ liệu không bị xóa vật lý khỏi database."
    )
    @DeleteMapping("/{conditionId}")
    public ApiResponse<Void> deleteCondition(
            @Parameter(
                    description = "ID của khuyến mãi chứa điều kiện",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Parameter(
                    description = "ID của điều kiện cần xóa",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440001"
            )
            @PathVariable UUID conditionId
    ) {
        promotionConditionService.deleteCondition(promotionId, conditionId);

        return ApiResponse.deleted(PromotionConditionSuccessMessage.DELETED);
    }
}