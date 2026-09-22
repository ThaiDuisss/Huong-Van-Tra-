package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.domain.response.ApiResponse;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.promotionservice.constant.ApiPath;
import org.hvtra.promotionservice.constant.event.EventSuccessMessage;
import org.hvtra.promotionservice.dto.request.event.EventCreateRequest;
import org.hvtra.promotionservice.dto.request.event.EventExtendRequest;
import org.hvtra.promotionservice.dto.request.event.EventFilterRequest;
import org.hvtra.promotionservice.dto.request.event.EventUpdateRequest;
import org.hvtra.promotionservice.dto.response.EventResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.service.EventService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.hvtra.promotionservice.constant.EventApiParameter.*;

@Validated
@RestController
@RequestMapping(ApiPath.EVENTS)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý sự kiện",
        description = "Các API dùng để quản lý các sự kiện khuyến mãi như Tết, Black Friday, khai trương hoặc các chiến dịch marketing thủ công."
)
public class EventController {

    EventService eventService;

    @Operation(
            summary = "Tạo sự kiện",
            description = "Tạo một sự kiện khuyến mãi mới. Sự kiện là ngữ cảnh nghiệp vụ dùng để nhóm nhiều chương trình khuyến mãi."
    )
    @UserAction(value = "EVENT_CREATE", entityType = "Event", entityId = "#result.data.id")
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
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.getEventById(eventId);
        return ApiResponse.success(EventSuccessMessage.FETCHED, response);
    }

    @Operation(
            summary = "Cập nhật sự kiện",
            description = "Cập nhật thông tin cơ bản của sự kiện như tên, mô tả, loại sự kiện, đối tượng áp dụng, thời gian bắt đầu và thời gian kết thúc."
    )
    @UserAction(value = "EVENT_UPDATE", entityType = "Event", entityId = "#eventId")
    @PutMapping("/{eventId}")
    public ApiResponse<EventResponse> updateEvent(
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable UUID eventId,

            @Valid @RequestBody EventUpdateRequest request
    ) {
        EventResponse response = eventService.updateEvent(eventId, request);
        return ApiResponse.updated(EventSuccessMessage.UPDATED, response);
    }

    @Operation(
            summary = "Xóa sự kiện",
            description = "Xóa mềm một sự kiện/chương trình khuyến mãi. Dữ liệu vẫn được lưu trong cơ sở dữ liệu và không bị xóa vật lý."
    )
    @UserAction(value = "EVENT_DELETE", entityType = "Event", entityId = "#eventId")
    @DeleteMapping("/{eventId}")
    public ApiResponse<Void> deleteEvent(
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable UUID eventId
    ) {
        eventService.deleteEvent(eventId);
        return ApiResponse.deleted(EventSuccessMessage.DELETED);
    }

    @Operation(
            summary = "Lên lịch sự kiện",
            description = "Chuyển sự kiện/chương trình khuyến mãi sang trạng thái SCHEDULED. Sự kiện đã lên lịch sẽ chờ tới thời gian bắt đầu để được kích hoạt."
    )
    @UserAction(value = "EVENT_SCHEDULE", entityType = "Event", entityId = "#eventId")
    @PostMapping("/{eventId}/schedule")
    public ApiResponse<EventResponse> scheduleEvent(
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.scheduleEvent(eventId);
        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Kích hoạt sự kiện",
            description = "Kích hoạt một sự kiện/chương trình khuyến mãi để các chương trình khuyến mãi thuộc sự kiện có thể được áp dụng."
    )
    @UserAction(value = "EVENT_ACTIVATE", entityType = "Event", entityId = "#eventId")
    @PostMapping("/{eventId}/activate")
    public ApiResponse<EventResponse> activateEvent(
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.activateEvent(eventId);
        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Tạm dừng sự kiện",
            description = "Tạm dừng một sự kiện/chương trình khuyến mãi đang hoạt động. Chỉ sự kiện ACTIVE mới có thể chuyển sang PAUSED."
    )
    @UserAction(value = "EVENT_PAUSE", entityType = "Event", entityId = "#eventId")
    @PostMapping("/{eventId}/pause")
    public ApiResponse<EventResponse> pauseEvent(
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.pauseEvent(eventId);
        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Tiếp tục sự kiện",
            description = "Tiếp tục một sự kiện/chương trình khuyến mãi đang tạm dừng. Nếu đã tới thời gian chạy thì chuyển về ACTIVE, nếu thời gian bắt đầu ở tương lai thì chuyển về SCHEDULED."
    )
    @UserAction(value = "EVENT_RESUME", entityType = "Event", entityId = "#eventId")
    @PostMapping("/{eventId}/resume")
    public ApiResponse<EventResponse> resumeEvent(
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.resumeEvent(eventId);
        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Hủy sự kiện",
            description = "Hủy một sự kiện/chương trình khuyến mãi. Sự kiện đã hủy sẽ không thể được kích hoạt lại."
    )
    @UserAction(value = "EVENT_CANCEL", entityType = "Event", entityId = "#eventId")
    @PostMapping("/{eventId}/cancel")
    public ApiResponse<EventResponse> cancelEvent(
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable UUID eventId
    ) {
        EventResponse response = eventService.cancelEvent(eventId);
        return ApiResponse.updated(EventSuccessMessage.STATUS_UPDATED, response);
    }

    @Operation(
            summary = "Kết thúc sự kiện",
            description = "Đánh dấu sự kiện/chương trình khuyến mãi là đã kết thúc. Sự kiện đã kết thúc sẽ không thể được kích hoạt lại."
    )
    @UserAction(value = "EVENT_END", entityType = "Event", entityId = "#eventId")
    @PostMapping("/{eventId}/end")
    public ApiResponse<EventResponse> endEvent(
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
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
        PageResponse<EventResponse> response = eventService.getActiveEvents(page, size);
        return ApiResponse.success(EventSuccessMessage.LIST_FETCHED, response);
    }

    @Operation(
            summary = "Gia hạn sự kiện",
            description = "Gia hạn thời gian kết thúc của sự kiện/chương trình khuyến mãi. Nếu sự kiện đã EXPIRED thì sau khi gia hạn thành công sẽ được kích hoạt lại."
    )
    @UserAction(value = "EVENT_EXTEND", entityType = "Event", entityId = "#eventId")
    @PatchMapping("/{eventId}/extend")
    public ApiResponse<EventResponse> extendEvent(
            @Parameter(
                    name = EVENT_ID,
                    description = EVENT_ID_DESCRIPTION,
                    required = true,
                    in = ParameterIn.PATH,
                    example = EVENT_ID_EXAMPLE,
                    schema = @Schema(type = "string", format = "uuid")
            )
            @PathVariable UUID eventId,

            @Valid @RequestBody EventExtendRequest request
    ) {
        EventResponse response = eventService.extendEvent(eventId, request);
        return ApiResponse.updated(EventSuccessMessage.UPDATED, response);
    }

    @Operation(
            summary = "Lấy danh sách sự kiện",
            description = "Lấy danh sách các sự kiện dưới dạng phân trang, hỗ trợ filter theo keyword, trạng thái, loại sự kiện, đối tượng và thời gian."
    )
    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> getAllEvents(
            @Valid @ModelAttribute EventFilterRequest filter,

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
        PageResponse<EventResponse> response = eventService.getEvents(filter, page, size);
        return ApiResponse.success(EventSuccessMessage.LIST_FETCHED, response);
    }


}