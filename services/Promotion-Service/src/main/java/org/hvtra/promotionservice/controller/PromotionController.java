package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.commonservice.logging.action.UserAction;
import org.hvtra.promotionservice.constant.ApiPath;
import org.hvtra.promotionservice.constant.promotion.PromotionSuccessMessage;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionFilterRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.service.PromotionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.hvtra.promotionservice.constant.EventApiParameter.*;

@Validated
@RestController
@RequestMapping(ApiPath.PROMOTION_SERVICE)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý khuyến mãi",
        description = "Nhóm API dùng để quản lý các chương trình khuyến mãi thuộc event/campaign marketing"
)
public class PromotionController {

    PromotionService promotionService;

    @Operation(
            summary = "Tạo khuyến mãi",
            description = "Tạo mới một chương trình khuyến mãi. Request cần truyền eventId."
    )
    @UserAction(value = "PROMOTION_CREATE", entityType = "Promotion", entityId = "#result.data.id")
    @PostMapping
    public ApiResponse<PromotionResponse> createPromotion(
            @Valid @RequestBody PromotionCreateRequest request
    ) {
        PromotionResponse response = promotionService.createPromotion(request);

        return ApiResponse.created(PromotionSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Tạo khuyến mãi theo event",
            description = "Tạo mới một chương trình khuyến mãi trực tiếp dưới một event/campaign cụ thể."
    )
    @UserAction(value = "PROMOTION_CREATE", entityType = "Promotion", entityId = "#result.data.id", detail = "#eventId")
    @PostMapping("/events/{eventId}/promotions")
    public ApiResponse<PromotionResponse> createPromotionByEvent(
            @Parameter(
                    description = "ID của event/campaign mà khuyến mãi thuộc về",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId,

            @Valid @RequestBody PromotionCreateRequest request
    ) {
        PromotionResponse response = promotionService.createPromotionByEvent(eventId, request);

        return ApiResponse.created(PromotionSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Lấy chi tiết khuyến mãi",
            description = "Lấy thông tin chi tiết của một chương trình khuyến mãi theo ID."
    )
    @GetMapping("/{promotionId}")
    public ApiResponse<PromotionResponse> getPromotionById(
            @Parameter(
                    description = "ID của khuyến mãi cần lấy thông tin",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        PromotionResponse response = promotionService.getPromotionById(promotionId);

        return ApiResponse.success(PromotionSuccessMessage.FETCHED, response);
    }

    @Operation(
            summary = "Lấy danh sách khuyến mãi",
            description = "Lấy danh sách các chương trình khuyến mãi chưa bị xóa mềm, có hỗ trợ phân trang."
    )
    @GetMapping
    public ApiResponse<PageResponse<PromotionResponse>> getAllPromotions(
            @Valid @ModelAttribute PromotionFilterRequest filter,

            @Parameter(
                    name = PAGE,
                    description = PAGE_DESCRIPTION,
                    example = PAGE_EXAMPLE
            )
            @RequestParam(defaultValue = PAGE_DEFAULT)
            @PositiveOrZero(message = "Page index must be greater than or equal to 0")
            int page,

            @Parameter(
                    name = SIZE,
                    description = SIZE_DESCRIPTION,
                    example = SIZE_EXAMPLE
            )
            @RequestParam(defaultValue = SIZE_DEFAULT)
            @Min(value = 1, message = "Page size must be greater than or equal to 1")
            @Max(value = SIZE_MAX, message = "Page size must be less than or equal to 100")
            int size
    ) {
        PageResponse<PromotionResponse> response = promotionService.getPromotions(filter, page, size);

        return ApiResponse.success(PromotionSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Lấy danh sách khuyến mãi theo event",
            description = "Lấy danh sách các chương trình khuyến mãi thuộc một event/campaign cụ thể."
    )
    @GetMapping("/events/{eventId}/promotions")
    public ApiResponse<PageResponse<PromotionResponse>> getPromotionsByEvent(
            @Parameter(
                    description = "ID của event/campaign cần lấy danh sách khuyến mãi",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId,

            @Valid @ModelAttribute PromotionFilterRequest filter,

            @Parameter(
                    name = PAGE,
                    description = PAGE_DESCRIPTION,
                    example = PAGE_EXAMPLE
            )
            @RequestParam(defaultValue = PAGE_DEFAULT)
            @PositiveOrZero(message = "Page index must be greater than or equal to 0")
            int page,

            @Parameter(
                    name = SIZE,
                    description = SIZE_DESCRIPTION,
                    example = SIZE_EXAMPLE
            )
            @RequestParam(defaultValue = SIZE_DEFAULT)
            @Min(value = 1, message = "Page size must be greater than or equal to 1")
            @Max(value = SIZE_MAX, message = "Page size must be less than or equal to 100")
            int size
    ) {
        PageResponse<PromotionResponse> response = promotionService.getPromotionsByEvent(eventId, filter, page, size);

        return ApiResponse.success(PromotionSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Cập nhật khuyến mãi",
            description = "Cập nhật thông tin cấu hình cơ bản của khuyến mãi."
    )
    @UserAction(value = "PROMOTION_UPDATE", entityType = "Promotion", entityId = "#promotionId")
    @PutMapping("/{promotionId}")
    public ApiResponse<PromotionResponse> updatePromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần cập nhật",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Valid @RequestBody PromotionUpdateRequest request
    ) {
        PromotionResponse response = promotionService.updatePromotion(promotionId, request);

        return ApiResponse.updated(PromotionSuccessMessage.UPDATED, response);
    }

    @Operation(
            summary = "Xóa khuyến mãi",
            description = "Xóa mềm một chương trình khuyến mãi."
    )
    @UserAction(value = "PROMOTION_DELETE", entityType = "Promotion", entityId = "#promotionId")
    @DeleteMapping("/{promotionId}")
    public ApiResponse<Void> deletePromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần xóa",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        promotionService.deletePromotion(promotionId);

        return ApiResponse.deleted(PromotionSuccessMessage.DELETED);
    }

    @Operation(
            summary = "Lên lịch khuyến mãi",
            description = "Chuyển khuyến mãi sang trạng thái SCHEDULED."
    )
    @UserAction(value = "PROMOTION_SCHEDULE", entityType = "Promotion", entityId = "#promotionId")
    @PostMapping("/{promotionId}/schedule")
    public ApiResponse<PromotionResponse> schedulePromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần lên lịch",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        PromotionResponse response = promotionService.schedulePromotion(promotionId);

        return ApiResponse.updated(PromotionSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Kích hoạt khuyến mãi",
            description = "Chuyển khuyến mãi sang trạng thái ACTIVE."
    )
    @UserAction(value = "PROMOTION_ACTIVATE", entityType = "Promotion", entityId = "#promotionId")
    @PostMapping("/{promotionId}/activate")
    public ApiResponse<PromotionResponse> activatePromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần kích hoạt",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        PromotionResponse response = promotionService.activatePromotion(promotionId);

        return ApiResponse.updated(PromotionSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Tạm dừng khuyến mãi",
            description = "Chuyển khuyến mãi sang trạng thái PAUSED."
    )
    @UserAction(value = "PROMOTION_PAUSE", entityType = "Promotion", entityId = "#promotionId")
    @PostMapping("/{promotionId}/pause")
    public ApiResponse<PromotionResponse> pausePromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần tạm dừng",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        PromotionResponse response = promotionService.pausePromotion(promotionId);

        return ApiResponse.updated(PromotionSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Tiếp tục khuyến mãi",
            description = "Resume khuyến mãi đang PAUSED. Nếu startAt ở tương lai thì về SCHEDULED, nếu đã tới thời gian chạy thì về ACTIVE."
    )
    @UserAction(value = "PROMOTION_RESUME", entityType = "Promotion", entityId = "#promotionId")
    @PostMapping("/{promotionId}/resume")
    public ApiResponse<PromotionResponse> resumePromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần tiếp tục",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        PromotionResponse response = promotionService.resumePromotion(promotionId);

        return ApiResponse.updated(PromotionSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Hủy khuyến mãi",
            description = "Chuyển khuyến mãi sang trạng thái CANCELLED."
    )
    @UserAction(value = "PROMOTION_CANCEL", entityType = "Promotion", entityId = "#promotionId")
    @PostMapping("/{promotionId}/cancel")
    public ApiResponse<PromotionResponse> cancelPromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần hủy",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        PromotionResponse response = promotionService.cancelPromotion(promotionId);

        return ApiResponse.updated(PromotionSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Đánh dấu khuyến mãi hết hạn",
            description = "Chuyển khuyến mãi sang trạng thái EXPIRED. Thường dùng cho scheduler/system."
    )
    @UserAction(value = "PROMOTION_EXPIRE", entityType = "Promotion", entityId = "#promotionId")
    @PostMapping("/{promotionId}/expire")
    public ApiResponse<PromotionResponse> expirePromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần đánh dấu hết hạn",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        PromotionResponse response = promotionService.expirePromotion(promotionId);

        return ApiResponse.updated(PromotionSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Kết thúc khuyến mãi",
            description = "Chuyển khuyến mãi sang trạng thái ENDED. Dùng khi admin chủ động đóng/kết thúc promotion."
    )
    @UserAction(value = "PROMOTION_END", entityType = "Promotion", entityId = "#promotionId")
    @PostMapping("/{promotionId}/end")
    public ApiResponse<PromotionResponse> endPromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần kết thúc",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        PromotionResponse response = promotionService.endPromotion(promotionId);

        return ApiResponse.updated(PromotionSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Sao chép khuyến mãi",
            description = "Tạo một bản sao mới từ khuyến mãi hiện tại. Bản sao mặc định ở trạng thái DRAFT."
    )
    @UserAction(value = "PROMOTION_DUPLICATE", entityType = "Promotion", entityId = "#result.data.id", detail = "#promotionId")
    @PostMapping("/{promotionId}/duplicate")
    public ApiResponse<PromotionResponse> duplicatePromotion(
            @Parameter(
                    description = "ID của khuyến mãi cần sao chép",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId
    ) {
        PromotionResponse response = promotionService.duplicatePromotion(promotionId);

        return ApiResponse.created(PromotionSuccessMessage.CREATED, response);
    }
}
