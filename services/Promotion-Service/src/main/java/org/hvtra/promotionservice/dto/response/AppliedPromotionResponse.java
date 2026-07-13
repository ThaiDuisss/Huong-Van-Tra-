package org.hvtra.promotionservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.hvtra.promotionservice.enums.benefit.BenefitType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppliedPromotionResponse {

    UUID promotionId;

    String promotionName;

    @Schema(description = "Loại quyền lợi khuyến mãi đã áp dụng", example = "PERCENT_DISCOUNT", implementation = BenefitType.class)
    BenefitType benefitType;

    BigDecimal discountAmount;
}
