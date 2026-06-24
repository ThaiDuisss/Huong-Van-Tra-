package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.promotionservice.constant.event.EventSuccessMessage;
import org.hvtra.promotionservice.dto.request.event.EventCreateRequest;
import org.hvtra.promotionservice.dto.request.event.EventStatusUpdateRequest;
import org.hvtra.promotionservice.dto.request.event.EventUpdateRequest;
import org.hvtra.promotionservice.dto.response.EventResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.service.EventService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý sự kiện khuyến mãi",
        description = "Nhóm API dùng để quản lý các sự kiện hoặc chiến dịch marketing của hệ thống Hương Vân Trà"
)
public class EventController {

    EventService eventService;

    @Operation(
            summary = "Tạo sự kiện khuyến mãi",
            description = "Tạo mới một sự kiện marketing, ví dụ: Tết, Black Friday, khai trương chi nhánh hoặc chương trình theo mùa."
    )
    @PostMapping
    public ApiResponse<EventResponse> createEvent(
            @Valid @RequestBody EventCreateRequest request
    ) {
        EventResponse response = eventService.createEvent(request);

        return ApiResponse.created(EventSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Lấy chi tiết sự kiện",
            description = "Lấy thông tin chi tiết của một sự kiện khuyến mãi theo ID."
    )
    @GetMapping("/{eventId}")
    public ApiResponse<EventResponse> getEventById(
            @Parameter(
                    description = "ID của sự kiện cần lấy thông tin",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.getEventById(eventId);

        return ApiResponse.success(EventSuccessMessage.FETCHED, response);
    }

    @Operation(
            summary = "Lấy danh sách sự kiện",
            description = "Lấy danh sách tất cả sự kiện khuyến mãi chưa bị xóa, có phân trang."
    )
    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> getAllEvents(
            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng phần tử mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<EventResponse> response = eventService.getAllEvents(page, size);

        return ApiResponse.success(EventSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Cập nhật sự kiện",
            description = "Cập nhật thông tin cơ bản của sự kiện như tên, mô tả, thời gian bắt đầu và thời gian kết thúc."
    )
    @PutMapping("/{eventId}")
    public ApiResponse<EventResponse> updateEvent(
            @Parameter(
                    description = "ID của sự kiện cần cập nhật",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId,

            @Valid @RequestBody EventUpdateRequest request
    ) {
        EventResponse response = eventService.updateEvent(eventId, request);

        return ApiResponse.updated(EventSuccessMessage.UPDATED, response);
    }

    @Operation(
            summary = "Cập nhật trạng thái sự kiện",
            description = "Thay đổi trạng thái của sự kiện. Ví dụ: ACTIVE, INACTIVE, ENDED hoặc CANCELLED."
    )
    @PatchMapping("/{eventId}/status")
    public ApiResponse<EventResponse> updateEventStatus(
            @Parameter(
                    description = "ID của sự kiện cần cập nhật trạng thái",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable UUID eventId,

            @Valid @RequestBody EventStatusUpdateRequest request
    ) {
        EventResponse response = eventService.updateEventStatus(eventId, request);

        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Xóa sự kiện",
            description = "Xóa mềm một sự kiện khuyến mãi. Dữ liệu không bị xóa vật lý khỏi database mà chỉ được đánh dấu là đã xóa."
    )
    @Parameter(
            name = "eventId",
            description = "ID của sự kiện cần xóa",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    @DeleteMapping("/{eventId}")
    public ApiResponse<Void> deleteEvent(
            @PathVariable UUID eventId
    ) {
        eventService.deleteEvent(eventId);

        return ApiResponse.deleted(EventSuccessMessage.DELETED);
    }
}