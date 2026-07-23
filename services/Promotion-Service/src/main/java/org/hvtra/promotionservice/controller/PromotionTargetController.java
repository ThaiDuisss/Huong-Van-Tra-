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
import org.hvtra.promotionservice.constant.promotiontarget.PromotionTargetSuccessMessage;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetCreateRequest;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionTargetResponse;
import org.hvtra.promotionservice.service.PromotionTargetService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPath.PROMOTION_SERVICE + "/{promotionId}/targets")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý phạm vi áp dụng khuyến mãi",
        description = "Nhóm API dùng để quản lý sản phẩm, danh mục hoặc thương hiệu được áp dụng/loại trừ khỏi khuyến mãi"
)
public class PromotionTargetController {

    PromotionTargetService promotionTargetService;

    @Operation(
            summary = "Tạo phạm vi áp dụng khuyến mãi",
            description = "Tạo mới target cho khuyến mãi, ví dụ áp dụng cho một sản phẩm, danh mục hoặc thương hiệu cụ thể."
    )
    @PostMapping
    public ApiResponse<PromotionTargetResponse> createTarget(
            @Parameter(description = "ID của khuyến mãi cần thêm target", required = true)
            @PathVariable UUID promotionId,

            @Valid @RequestBody PromotionTargetCreateRequest request
    ) {
        PromotionTargetResponse response = promotionTargetService.createTarget(promotionId, request);
        return ApiResponse.created(PromotionTargetSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Lấy danh sách phạm vi áp dụng",
            description = "Lấy danh sách target chưa bị xóa mềm của một khuyến mãi, có hỗ trợ phân trang."
    )
    @GetMapping
    public ApiResponse<PageResponse<PromotionTargetResponse>> getTargetsByPromotion(
            @Parameter(description = "ID của khuyến mãi cần lấy danh sách target", required = true)
            @PathVariable UUID promotionId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PromotionTargetResponse> response =
                promotionTargetService.getTargetsByPromotion(promotionId, page, size);

        return ApiResponse.success(PromotionTargetSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Lấy chi tiết phạm vi áp dụng",
            description = "Lấy thông tin chi tiết của một target thuộc khuyến mãi."
    )
    @GetMapping("/{targetId}")
    public ApiResponse<PromotionTargetResponse> getTargetById(
            @Parameter(description = "ID của khuyến mãi chứa target", required = true)
            @PathVariable UUID promotionId,

            @Parameter(description = "ID của target cần lấy thông tin", required = true)
            @PathVariable UUID targetId
    ) {
        PromotionTargetResponse response =
                promotionTargetService.getTargetById(promotionId, targetId);

        return ApiResponse.success(PromotionTargetSuccessMessage.FETCHED, response);
    }

    @Operation(
            summary = "Cập nhật phạm vi áp dụng",
            description = "Cập nhật target của khuyến mãi, bao gồm loại target, targetId, trạng thái loại trừ và thông tin override quyền lợi."
    )
    @PutMapping("/{targetId}")
    public ApiResponse<PromotionTargetResponse> updateTarget(
            @Parameter(description = "ID của khuyến mãi chứa target", required = true)
            @PathVariable UUID promotionId,

            @Parameter(description = "ID của target cần cập nhật", required = true)
            @PathVariable UUID targetId,

            @Valid @RequestBody PromotionTargetUpdateRequest request
    ) {
        PromotionTargetResponse response =
                promotionTargetService.updateTarget(promotionId, targetId, request);

        return ApiResponse.updated(PromotionTargetSuccessMessage.UPDATED, response);
    }

    @Operation(
            summary = "Xóa phạm vi áp dụng",
            description = "Xóa mềm một target của khuyến mãi. Dữ liệu không bị xóa vật lý khỏi database."
    )
    @DeleteMapping("/{targetId}")
    public ApiResponse<Void> deleteTarget(
            @Parameter(description = "ID của khuyến mãi chứa target", required = true)
            @PathVariable UUID promotionId,

            @Parameter(description = "ID của target cần xóa", required = true)
            @PathVariable UUID targetId
    ) {
        promotionTargetService.deleteTarget(promotionId, targetId);
        return ApiResponse.deleted(PromotionTargetSuccessMessage.DELETED);
    }
}
