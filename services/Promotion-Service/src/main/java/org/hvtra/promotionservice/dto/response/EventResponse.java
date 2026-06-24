package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.event.EventStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response thông tin sự kiện khuyến mãi")
public class EventResponse {

    @Schema(description = "ID sự kiện khuyến mãi", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id;

    @Schema(description = "Tên sự kiện khuyến mãi", example = "Tet Holiday Campaign 2027")
    String name;

    @Schema(description = "Mô tả chi tiết về sự kiện khuyến mãi", example = "Promotion campaign for Tet holiday season")
    String description;

    @Schema(description = "Thời gian bắt đầu sự kiện", example = "2027-02-01T00:00:00")
    LocalDateTime startAt;

    @Schema(description = "Thời gian kết thúc sự kiện", example = "2027-02-15T23:59:59")
    LocalDateTime endAt;

    @Schema(description = "Trạng thái sự kiện", example = "ACTIVE")
    EventStatus status;

    @Builder.Default
    @Schema(description = "Danh sách khuyến mãi thuộc sự kiện")
    List<PromotionResponse> promotions = new ArrayList<>();

    @Schema(description = "Thời gian tạo", example = "2026-06-24T10:30:00")
    Instant createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất", example = "2026-06-24T11:00:00")
    Instant updatedAt;
}
