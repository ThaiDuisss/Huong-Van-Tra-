package org.hvtra.promotionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailablePromotionResponse {

    UUID promotionId;

    String promotionName;

    PromotionApplyMethod applyMethod;

    BenefitType benefitType;

    BigDecimal estimatedDiscount;

    Boolean stackable;
}
