package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "EventResponse",
        description = "Thông tin phản hồi chi tiết của sự kiện marketing/chiến dịch khuyến mãi"
)
public class EventResponse {

    @Schema(
            description = "Mã định danh duy nhất của sự kiện",
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    UUID id;

    @Schema(
            description = "Tên sự kiện marketing hoặc chiến dịch khuyến mãi",
            example = "Black Friday 2026"
    )
    String name;

    @Schema(
            description = "Mô tả chi tiết về mục tiêu, nội dung hoặc phạm vi áp dụng của sự kiện",
            example = "Chiến dịch khuyến mãi giảm giá lớn áp dụng cho toàn bộ người dùng trong dịp Black Friday"
    )
    String description;

    @Schema(
            description = "Thời điểm bắt đầu sự kiện",
            example = "2026-11-27T00:00:00"
    )
    LocalDateTime startAt;

    @Schema(
            description = "Thời điểm kết thúc sự kiện",
            example = "2026-11-30T23:59:59"
    )
    LocalDateTime endAt;

    @Schema(
            description = "Loại sự kiện marketing",
            example = "SEASONAL"
    )
    EventType eventType;

    @Schema(
            description = "Nhóm đối tượng người dùng mà sự kiện hướng tới",
            example = "ALL_USERS"
    )
    EventAudience audience;

    @Schema(
            description = "Trạng thái hiện tại của sự kiện",
            example = "ACTIVE"
    )
    EventStatus status;

    @Schema(
            description = "Cờ đánh dấu sự kiện đã bị xóa mềm hay chưa",
            example = "false"
    )
    Boolean isDeleted;

    @Schema(
            description = "Mã định danh của người tạo sự kiện",
            example = "6f1c2f44-8b8e-4b8e-9e3a-2d0f5c9a7a11"
    )
    UUID createdBy;

    @Schema(
            description = "Mã định danh của người cập nhật sự kiện gần nhất",
            example = "9a7b1f33-4e1a-4c0d-9b67-93df1f3a2c22"
    )
    UUID updatedBy;

    @Schema(
            description = "Thời điểm bản ghi sự kiện được tạo trong hệ thống",
            example = "2026-06-28T04:15:30Z"
    )
    Instant createdAt;

    @Schema(
            description = "Thời điểm bản ghi sự kiện được cập nhật gần nhất",
            example = "2026-06-28T04:30:45Z"
    )
    Instant updatedAt;

    @Schema(
            description = "Phiên bản của bản ghi, dùng cho cơ chế optimistic locking",
            example = "1"
    )
    Long version;
}