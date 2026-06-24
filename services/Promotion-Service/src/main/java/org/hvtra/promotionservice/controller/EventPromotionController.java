package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.promotionservice.constant.promotion.PromotionSuccessMessage;
import org.hvtra.promotionservice.dto.request.eventpromotion.EventPromotionRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.service.EventPromotionService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/promotions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý khuyến mãi trong sự kiện",
        description = "Nhóm API dùng để tạo, gắn, gỡ và lấy danh sách khuyến mãi thuộc một sự kiện"
)
public class EventPromotionController {

    EventPromotionService eventPromotionService;

    @Operation(
            summary = "Lấy danh sách khuyến mãi thuộc sự kiện",
            description = "Lấy danh sách các khuyến mãi đang được gắn với một sự kiện, có phân trang."
    )
    @GetMapping
    public ApiResponse<PageResponse<PromotionResponse>> getPromotionsByEvent(
            @Parameter(
                    description = "ID của sự kiện",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId,

            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PromotionResponse> response =
                eventPromotionService.getPromotionsByEvent(eventId, page, size);

        return ApiResponse.success(
                PromotionSuccessMessage.LIST_FETCHED,
                response
        );
    }

    @Operation(
            summary = "Tạo mới khuyến mãi trong sự kiện",
            description = "Tạo mới một khuyến mãi và gắn trực tiếp khuyến mãi đó vào sự kiện."
    )
    @PostMapping("/create")
    public ApiResponse<PromotionResponse> createPromotionInEvent(
            @Parameter(
                    description = "ID của sự kiện",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId,

            @Valid @RequestBody PromotionCreateRequest request
    ) {
        PromotionResponse response =
                eventPromotionService.createPromotionInEvent(eventId, request);

        return ApiResponse.created(
                PromotionSuccessMessage.CREATED,
                response
        );
    }

    @Operation(
            summary = "Gắn khuyến mãi vào sự kiện",
            description = "Gắn một hoặc nhiều khuyến mãi có sẵn vào sự kiện."
    )
    @PostMapping
    public ApiResponse<Void> attachPromotionsToEvent(
            @Parameter(
                    description = "ID của sự kiện",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId,

            @Valid @RequestBody EventPromotionRequest request
    ) {
        eventPromotionService.attachPromotionsToEvent(eventId, request);

        return ApiResponse.updated(PromotionSuccessMessage.UPDATED, null);
    }

    @Operation(
            summary = "Gỡ khuyến mãi khỏi sự kiện",
            description = "Gỡ một hoặc nhiều khuyến mãi khỏi sự kiện. Khuyến mãi không bị xóa, chỉ bỏ liên kết với sự kiện."
    )
    @DeleteMapping
    public ApiResponse<Void> detachPromotionsFromEvent(
            @Parameter(
                    description = "ID của sự kiện",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId,

            @Valid @RequestBody EventPromotionRequest request
    ) {
        eventPromotionService.detachPromotionsFromEvent(eventId, request);

        return ApiResponse.updated(PromotionSuccessMessage.UPDATED, null);
    }
}