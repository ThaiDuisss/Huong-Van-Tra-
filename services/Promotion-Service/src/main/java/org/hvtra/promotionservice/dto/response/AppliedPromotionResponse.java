package org.hvtra.promotionservice.dto.response;

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

    BenefitType benefitType;

    BigDecimal discountAmount;
}
