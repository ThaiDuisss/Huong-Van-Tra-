package org.hvtra.promotionservice.dto.request.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(
        name = "EventFilterRequest",
        description = "Thông tin lọc danh sách sự kiện khuyến mãi"
)
public class EventFilterRequest {

    @Schema(
            description = "Từ khóa tìm kiếm theo tên, mã hoặc mô tả sự kiện",
            example = "summer sale",
            maxLength = 100,
            nullable = true
    )
    @Size(max = 100, message = "Từ khóa không được vượt quá 100 ký tự")
    String keyword;

    @Schema(
            description = "Trạng thái hiện tại của sự kiện",
            example = "ACTIVE",
            implementation = EventStatus.class,
            nullable = true
    )
    EventStatus status;

    @Schema(
            description = "Loại sự kiện khuyến mãi",
            example = "SEASONAL_CAMPAIGN",
            implementation = EventType.class,
            nullable = true
    )
    EventType eventType;

    @Schema(
            description = "Nhóm khách hàng áp dụng sự kiện",
            example = "ALL_CUSTOMERS",
            implementation = EventAudience.class,
            nullable = true
    )
    EventAudience audience;

    @Schema(
            description = "Lọc các sự kiện có thời gian bắt đầu từ thời điểm này trở đi",
            example = "2026-07-01T00:00:00",
            type = "string",
            format = "date-time",
            nullable = true
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime startFrom;

    @Schema(
            description = "Lọc các sự kiện có thời gian bắt đầu đến thời điểm này",
            example = "2026-07-31T23:59:59",
            type = "string",
            format = "date-time",
            nullable = true
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime startTo;

    @Schema(
            description = "Lọc các sự kiện có thời gian kết thúc từ thời điểm này trở đi",
            example = "2026-07-01T00:00:00",
            type = "string",
            format = "date-time",
            nullable = true
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime endFrom;

    @Schema(
            description = "Lọc các sự kiện có thời gian kết thúc đến thời điểm này",
            example = "2026-07-31T23:59:59",
            type = "string",
            format = "date-time",
            nullable = true
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime endTo;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Thời gian bắt đầu từ không được sau thời gian bắt đầu đến")
    public boolean isValidStartRange() {
        return startFrom == null
                || startTo == null
                || !startFrom.isAfter(startTo);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Thời gian kết thúc từ không được sau thời gian kết thúc đến")
    public boolean isValidEndRange() {
        return endFrom == null
                || endTo == null
                || !endFrom.isAfter(endTo);
    }
}