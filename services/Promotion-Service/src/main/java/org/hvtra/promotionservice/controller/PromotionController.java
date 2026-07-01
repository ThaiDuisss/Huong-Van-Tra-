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
import org.hvtra.promotionservice.constant.promotion.PromotionSuccessMessage;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionStatusUpdateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.service.PromotionService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPath.PROMOTION_SERVICE + "/promotions")
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
            description = "Tạo mới một chương trình khuyến mãi. Khi gọi API này, request cần truyền eventId để xác định khuyến mãi thuộc event/campaign nào."
    )
    @PostMapping("/promotions")
    public ApiResponse<PromotionResponse> createPromotion(
            @Valid @RequestBody PromotionCreateRequest request
    ) {
        PromotionResponse response = promotionService.createPromotion(request);
        return ApiResponse.created(PromotionSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Tạo khuyến mãi theo event",
            description = "Tạo mới một chương trình khuyến mãi trực tiếp dưới một event/campaign cụ thể. eventId được lấy từ path variable."
    )
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
    @GetMapping("/promotions/{promotionId}")
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
    @GetMapping("/promotions")
    public ApiResponse<PageResponse<PromotionResponse>> getAllPromotions(
            @Parameter(
                    description = "Số trang, bắt đầu từ 0",
                    example = "0"
            )
            @RequestParam(defaultValue = "0") int page,

            @Parameter(
                    description = "Số lượng phần tử trên mỗi trang",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PromotionResponse> response = promotionService.getAllPromotions(page, size);
        return ApiResponse.success(PromotionSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Lấy danh sách khuyến mãi theo event",
            description = "Lấy danh sách các chương trình khuyến mãi thuộc một event/campaign cụ thể, có hỗ trợ phân trang."
    )
    @GetMapping("/events/{eventId}/promotions")
    public ApiResponse<PageResponse<PromotionResponse>> getPromotionsByEvent(
            @Parameter(
                    description = "ID của event/campaign cần lấy danh sách khuyến mãi",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId,

            @Parameter(
                    description = "Số trang, bắt đầu từ 0",
                    example = "0"
            )
            @RequestParam(defaultValue = "0") int page,

            @Parameter(
                    description = "Số lượng phần tử trên mỗi trang",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PromotionResponse> response = promotionService.getPromotionsByEvent(eventId, page, size);
        return ApiResponse.success(PromotionSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Cập nhật khuyến mãi",
            description = "Cập nhật cấu hình cơ bản của khuyến mãi như tên, mô tả, mục tiêu, loại khuyến mãi, cách áp dụng, thời gian áp dụng, độ ưu tiên và khả năng dùng chung với khuyến mãi khác."
    )
    @PutMapping("/promotions/{promotionId}")
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
            summary = "Cập nhật trạng thái khuyến mãi",
            description = "Cập nhật trạng thái vòng đời của khuyến mãi. Các trạng thái gồm: DRAFT, SCHEDULED, ACTIVE, INACTIVE, EXPIRED, CANCELLED."
    )
    @PatchMapping("/promotions/{promotionId}/status")
    public ApiResponse<PromotionResponse> updatePromotionStatus(
            @Parameter(
                    description = "ID của khuyến mãi cần cập nhật trạng thái",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID promotionId,

            @Valid @RequestBody PromotionStatusUpdateRequest request
    ) {
        PromotionResponse response = promotionService.updatePromotionStatus(promotionId, request);
        return ApiResponse.updated(PromotionSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Xóa khuyến mãi",
            description = "Xóa mềm một chương trình khuyến mãi. Dữ liệu không bị xóa vật lý khỏi database mà chỉ được đánh dấu là đã xóa."
    )
    @DeleteMapping("/promotions/{promotionId}")
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
            summary = "Kích hoạt khuyến mãi",
            description = "Chuyển khuyến mãi sang trạng thái ACTIVE. Chỉ kích hoạt được khi event cha đang ACTIVE và khuyến mãi chưa hết hạn."
    )
    @PostMapping("/promotions/{promotionId}/activate")
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
            description = "Chuyển khuyến mãi sang trạng thái INACTIVE. Dùng khi cần tạm khóa khuyến mãi nhưng vẫn có thể mở lại sau."
    )
    @PostMapping("/promotions/{promotionId}/pause")
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
            summary = "Hủy khuyến mãi",
            description = "Chuyển khuyến mãi sang trạng thái CANCELLED. Khuyến mãi đã hủy không nên được kích hoạt lại."
    )
    @PostMapping("/promotions/{promotionId}/cancel")
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
            description = "Chuyển khuyến mãi sang trạng thái EXPIRED. Dùng khi khuyến mãi đã qua thời gian áp dụng hoặc cần đóng thủ công."
    )
    @PostMapping("/promotions/{promotionId}/expire")
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
            summary = "Sao chép khuyến mãi",
            description = "Tạo một bản sao mới từ khuyến mãi hiện tại. Bản sao mặc định ở trạng thái DRAFT và không sao chép coupon/usage lịch sử."
    )
    @PostMapping("/promotions/{promotionId}/duplicate")
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