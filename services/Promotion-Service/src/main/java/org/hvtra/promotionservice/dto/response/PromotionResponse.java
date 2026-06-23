package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;

import java.math.BigDecimal;
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
@Schema(description = "Response thông tin khuyến mãi")
public class PromotionResponse {

    @Schema(description = "ID khuyến mãi", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id;

    @Schema(description = "Tên khuyến mãi", example = "Buy 2 Get 1 Free")
    String name;

    @Schema(description = "Mã khuyến mãi", example = "TET2027")
    String code;

    @Schema(description = "Mô tả khuyến mãi", example = "Discount promotion for Tet campaign")
    String description;

    @Schema(description = "Loại khuyến mãi", example = "PERCENTAGE")
    PromotionType type;

    @Schema(description = "Giá trị giảm", example = "10")
    BigDecimal discountValue;

    @Schema(description = "Trạng thái khuyến mãi", example = "ACTIVE")
    PromotionStatus status;

    @Schema(description = "Có cho phép cộng dồn không", example = "false")
    Boolean stackable;

    @Schema(description = "ID sự kiện chứa khuyến mãi", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID eventId;

    @Schema(description = "Tên sự kiện chứa khuyến mãi", example = "Tet Holiday Campaign 2027")
    String eventName;

    @Schema(description = "Thời gian tạo", example = "2026-06-24T10:30:00")
    Instant createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất", example = "2026-06-24T11:00:00")
    Instant updatedAt;
}
