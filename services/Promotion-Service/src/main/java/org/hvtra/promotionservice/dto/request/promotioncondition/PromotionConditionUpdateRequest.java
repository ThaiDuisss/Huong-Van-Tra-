package org.hvtra.promotionservice.dto.request.promotioncondition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.condition.ConditionOperator;
import org.hvtra.promotionservice.enums.condition.ConditionType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request cập nhật điều kiện áp dụng khuyến mãi")
public class PromotionConditionUpdateRequest {

    @Schema(
            description = "Loại điều kiện áp dụng khuyến mãi",
            example = "MIN_ORDER_AMOUNT",
            implementation = ConditionType.class
    )
    @NotNull(message = "Promotion condition type is required")
    ConditionType conditionType;

    @Schema(
            description = "Toán tử so sánh điều kiện",
            example = "GTE",
            implementation = ConditionOperator.class
    )
    @NotNull(message = "Promotion condition operator is required")
    ConditionOperator operator;

    @Schema(
            description = "Giá trị điều kiện ở dạng JSON string",
            example = "{\"amount\":500000}"
    )
    @NotBlank(message = "Promotion condition value is required")
    @Size(max = 2000, message = "Promotion condition value must not exceed 2000 characters")
    String conditionValue;
}
