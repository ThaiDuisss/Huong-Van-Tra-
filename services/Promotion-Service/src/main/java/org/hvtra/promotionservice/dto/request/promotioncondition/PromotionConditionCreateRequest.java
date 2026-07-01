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
@Schema(description = "Request tạo mới điều kiện áp dụng khuyến mãi")
public class PromotionConditionCreateRequest {

    @Schema(
            description = "Loại điều kiện áp dụng khuyến mãi",
            example = "MIN_ORDER_AMOUNT",
            allowableValues = {
                    "MIN_ORDER_AMOUNT",
                    "MAX_ORDER_AMOUNT",
                    "MIN_ITEM_QUANTITY",
                    "MIN_ORDER_COUNT",
                    "MIN_TOTAL_SPENT",
                    "MIN_LOYALTY_POINT",
                    "FIRST_PURCHASE",
                    "CUSTOMER_TIER",
                    "CUSTOMER_ID",
                    "CHANNEL",
                    "BRANCH",
                    "PAYMENT_METHOD"
            }
    )
    @NotNull(message = "Promotion condition type is required")
    ConditionType conditionType;

    @Schema(
            description = "Toán tử so sánh điều kiện",
            example = "GTE",
            allowableValues = {"EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN"}
    )
    @NotNull(message = "Promotion condition operator is required")
    ConditionOperator operator;

    @Schema(
            description = "Giá trị điều kiện ở dạng JSON string. Ví dụ đơn tối thiểu: {\"amount\":500000}, kênh bán: {\"channels\":[\"POS\",\"ONLINE\"]}",
            example = "{\"amount\":500000}"
    )
    @NotBlank(message = "Promotion condition value is required")
    @Size(max = 2000, message = "Promotion condition value must not exceed 2000 characters")
    String conditionValue;
}