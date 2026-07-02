package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.promotionservice.constant.ApiPath;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Validated
@RestController
@RequestMapping(ApiPath.EVENTS)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Quản lý enum của event",
        description = "Các API dùng để quản lý danh sách enum của event."
)
public class OptionController {
    @Operation(
            summary = "Lấy danh sách loại sự kiện",
            description = "Trả về danh sách EventType để FE render dropdown tạo/cập nhật sự kiện."
    )
    @GetMapping("/event-types")
    public ApiResponse<List<String>> getEventTypes() {
        List<String> eventTypes = Arrays.stream(EventType.values())
                .map(Enum::name)
                .toList();

        return ApiResponse.success("Event types fetched successfully", eventTypes);
    }

    @Operation(
            summary = "Lấy danh sách nhóm khách hàng áp dụng",
            description = "Trả về danh sách EventAudience để FE render dropdown tạo/cập nhật sự kiện."
    )
    @GetMapping("/event-audiences")
    public ApiResponse<List<String>> getEventAudiences() {
        List<String> audiences = Arrays.stream(EventAudience.values())
                .map(Enum::name)
                .toList();

        return ApiResponse.success("Event audiences fetched successfully", audiences);
    }

    @Operation(
            summary = "Lấy danh sách trạng thái sự kiện",
            description = "Trả về danh sách EventStatus để FE render filter/badge trạng thái."
    )
    @GetMapping("/event-statuses")
    public ApiResponse<List<String>> getEventStatuses() {
        List<String> statuses = Arrays.stream(EventStatus.values())
                .map(Enum::name)
                .toList();

        return ApiResponse.success("Event statuses fetched successfully", statuses);
    }
}
