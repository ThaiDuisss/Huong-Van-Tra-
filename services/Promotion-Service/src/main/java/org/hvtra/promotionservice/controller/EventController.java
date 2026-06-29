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
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý sự kiện",
        description = "Các API dùng để quản lý các sự kiện/chương trình khuyến mãi như Tết, Black Friday, khai trương hoặc các chiến dịch marketing thủ công."
)
public class EventController {

    EventService eventService;

    @Operation(
            summary = "Tạo sự kiện",
            description = "Tạo một sự kiện/chương trình khuyến mãi mới. Sự kiện là ngữ cảnh nghiệp vụ dùng để nhóm nhiều chương trình khuyến mãi."
    )
    @PostMapping
    public ApiResponse<EventResponse> createEvent(
            @Valid @RequestBody EventCreateRequest request
    ) {
        EventResponse response = eventService.createEvent(request);
        return ApiResponse.created(EventSuccessMessage.CREATED, response);
    }

    @Operation(
            summary = "Lấy thông tin sự kiện theo ID",
            description = "Lấy thông tin chi tiết của một sự kiện/chương trình khuyến mãi theo ID."
    )
    @GetMapping("/{eventId}")
    public ApiResponse<EventResponse> getEventById(
            @Parameter(description = "ID của sự kiện", required = true)
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.getEventById(eventId);
        return ApiResponse.success(EventSuccessMessage.FETCHED, response);
    }

    @Operation(
            summary = "Lấy danh sách sự kiện",
            description = "Lấy danh sách các sự kiện chưa bị xóa dưới dạng phân trang."
    )
    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> getAllEvents(
            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng bản ghi trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<EventResponse> response = eventService.getAllEvents(page, size);
        return ApiResponse.success(EventSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Cập nhật sự kiện",
            description = "Cập nhật thông tin cơ bản của sự kiện như tên, mô tả, loại sự kiện, đối tượng áp dụng, thời gian bắt đầu và thời gian kết thúc."
    )
    @PutMapping("/{eventId}")
    public ApiResponse<EventResponse> updateEvent(
            @Parameter(description = "ID của sự kiện", required = true)
            @PathVariable UUID eventId,

            @Valid @RequestBody EventUpdateRequest request
    ) {
        EventResponse response = eventService.updateEvent(eventId, request);
        return ApiResponse.updated(EventSuccessMessage.UPDATED, response);
    }

    @Operation(
            summary = "Cập nhật trạng thái sự kiện",
            description = "Cập nhật trạng thái vòng đời của sự kiện. Các giá trị hợp lệ gồm: ACTIVE, INACTIVE, ENDED, CANCELLED."
    )
    @PatchMapping("/{eventId}/status")
    public ApiResponse<EventResponse> updateEventStatus(
            @Parameter(description = "ID của sự kiện", required = true)
            @PathVariable UUID eventId,

            @Valid @RequestBody EventStatusUpdateRequest request
    ) {
        EventResponse response = eventService.updateEventStatus(eventId, request);
        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Xóa sự kiện",
            description = "Xóa mềm một sự kiện/chương trình khuyến mãi. Dữ liệu vẫn được lưu trong cơ sở dữ liệu và không bị xóa vật lý."
    )
    @DeleteMapping("/{eventId}")
    public ApiResponse<Void> deleteEvent(
            @Parameter(description = "ID của sự kiện", required = true)
            @PathVariable UUID eventId
    ) {
        eventService.deleteEvent(eventId);
        return ApiResponse.deleted(EventSuccessMessage.DELETED);
    }

    @Operation(
            summary = "Kích hoạt sự kiện",
            description = "Kích hoạt một sự kiện/chương trình khuyến mãi để các chương trình khuyến mãi thuộc sự kiện có thể được áp dụng."
    )
    @PostMapping("/{eventId}/activate")
    public ApiResponse<EventResponse> activateEvent(
            @Parameter(description = "ID của sự kiện", required = true)
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.activateEvent(eventId);
        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Hủy sự kiện",
            description = "Hủy một sự kiện/chương trình khuyến mãi. Sự kiện đã hủy sẽ không thể được kích hoạt lại."
    )
    @PostMapping("/{eventId}/cancel")
    public ApiResponse<EventResponse> cancelEvent(
            @Parameter(description = "ID của sự kiện", required = true)
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.cancelEvent(eventId);
        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Kết thúc sự kiện",
            description = "Đánh dấu sự kiện/chương trình khuyến mãi là đã kết thúc. Sự kiện đã kết thúc sẽ không thể được kích hoạt lại."
    )
    @PostMapping("/{eventId}/end")
    public ApiResponse<EventResponse> endEvent(
            @Parameter(description = "ID của sự kiện", required = true)
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.endEvent(eventId);
        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Lấy danh sách sự kiện đang hoạt động",
            description = "Lấy danh sách các sự kiện đang hoạt động và chưa bị xóa dưới dạng phân trang."
    )
    @GetMapping("/active")
    public ApiResponse<PageResponse<EventResponse>> getActiveEvents(
            @Parameter(description = "Số trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng bản ghi trên mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<EventResponse> response = eventService.getActiveEvents(page, size);
        return ApiResponse.success(EventSuccessMessage.LIST_FETCHED, response);
    }
}