package org.hvtra.promotionservice.dto.request.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.validation.annotation.ValidTimeRange;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request cập nhật sự kiện khuyến mãi")
@ValidTimeRange
public class EventUpdateRequest {

    @Schema(description = "Tên sự kiện khuyến mãi", example = "Tet Holiday Campaign 2027")
    @NotBlank(message = "Event name is required")
    @Size(max = 255, message = "Event name must not exceed 255 characters")
    String name;

    @Schema(description = "Mô tả chi tiết về sự kiện khuyến mãi", example = "Promotion campaign for Tet holiday season")
    @Size(max = 2000, message = "Event description must not exceed 2000 characters")
    String description;

    @Schema(description = "Thời gian bắt đầu sự kiện", example = "2027-02-01T00:00:00")
    @NotNull(message = "Start time is required")
    LocalDateTime startAt;

    @Schema(description = "Thời gian kết thúc sự kiện", example = "2027-02-15T23:59:59")
    @NotNull(message = "End time is required")
    LocalDateTime endAt;
}
