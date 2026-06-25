package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.condition.ConditionOperator;
import org.hvtra.promotionservice.enums.condition.ConditionType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response điều kiện khuyến mãi")
public class PromotionConditionResponse {

    @Schema(
            description = "ID điều kiện khuyến mãi",
            example = "550e8400-e29b-41d4-a716-446655440001"
    )
    UUID id;

    @Schema(
            description = "ID khuyến mãi chứa điều kiện này",
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    UUID promotionId;

    @Schema(
            description = "Loại điều kiện áp dụng khuyến mãi",
            example = "MIN_ORDER_AMOUNT"
    )
    ConditionType conditionType;

    @Schema(
            description = "Toán tử so sánh điều kiện",
            example = "GTE"
    )
    ConditionOperator operator;

    @Schema(
            description = "Giá trị của điều kiện",
            example = "500000"
    )
    String conditionValue;

    @Schema(description = "Thời gian tạo")
    Instant createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất")
    Instant updatedAt;
}