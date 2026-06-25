package org.hvtra.promotionservice.dto.request.promotionbenefit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request cập nhật quyền lợi khuyến mãi")
public class PromotionBenefitUpdateRequest {

    @Schema(
            description = "Loại quyền lợi khuyến mãi",
            example = "PERCENT_DISCOUNT"
    )
    @NotNull(message = "Promotion benefit type is required")
    BenefitType benefitType;

    @Schema(
            description = "Giá trị giảm. Với PERCENT_DISCOUNT là %, FIXED_AMOUNT là số tiền.",
            example = "15"
    )
    @NotNull(message = "Promotion benefit value is required")
    @DecimalMin(value = "0.0", inclusive = false,
            message = "Promotion benefit value must be greater than 0")
    BigDecimal value;

    @Schema(
            description = "Mức giảm tối đa",
            example = "80000"
    )
    @DecimalMin(value = "0.0",
            message = "Promotion max discount must be greater than or equal to 0")
    BigDecimal maxDiscount;
}
