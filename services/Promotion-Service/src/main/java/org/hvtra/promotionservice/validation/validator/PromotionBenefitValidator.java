package org.hvtra.promotionservice.validation.validator;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitCreateRequest;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitUpdateRequest;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionBenefitValidator {

    public void validateBeforeCreate(Promotion promotion, PromotionBenefitCreateRequest request) {
        validatePromotionEditable(promotion);
        validateBenefitConfig(
                request.getBenefitType(),
                request.getValue(),
                request.getMaxDiscount(),
                request.getGiftProductId(),
                request.getBuyQuantity(),
                request.getGetQuantity()
        );
    }

    public void validateBeforeUpdate(Promotion promotion, PromotionBenefitUpdateRequest request) {
        validatePromotionEditable(promotion);
        validateBenefitConfig(
                request.getBenefitType(),
                request.getValue(),
                request.getMaxDiscount(),
                request.getGiftProductId(),
                request.getBuyQuantity(),
                request.getGetQuantity()
        );
    }

    public void validateBeforeDelete(Promotion promotion) {
        validatePromotionEditable(promotion);
    }

    private void validatePromotionEditable(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.ACTIVE) {
            throw new BusinessException("Cannot modify benefits of active promotion");
        }

        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cannot modify benefits of cancelled promotion");
        }

        if (promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new BusinessException("Cannot modify benefits of expired promotion");
        }

        if (promotion.getStatus() == PromotionStatus.ENDED) {
            throw new BusinessException("Cannot modify benefits of ended promotion");
        }
    }

    private void validateBenefitConfig(
            BenefitType benefitType,
            BigDecimal value,
            BigDecimal maxDiscount,
            Object giftProductId,
            Integer buyQuantity,
            Integer getQuantity
    ) {
        if (benefitType == BenefitType.PERCENT_DISCOUNT) {
            if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessException("Percent discount value must be greater than 0 and less than or equal to 100");
            }
        }

        if (benefitType == BenefitType.FIXED_DISCOUNT) {
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Fixed discount value must be greater than 0");
            }
        }

        if (benefitType == BenefitType.FREE_SHIPPING) {
            if (maxDiscount != null && maxDiscount.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("Max discount must be greater than or equal to 0");
            }
        }

        if (benefitType == BenefitType.FREE_PRODUCT) {
            if (giftProductId == null) {
                throw new BusinessException("Gift product ID is required for free product benefit");
            }

            if (getQuantity == null || getQuantity < 1) {
                throw new BusinessException("Get quantity is required for free product benefit");
            }
        }

        if (benefitType == BenefitType.BUY_X_GET_Y) {
            if (giftProductId == null) {
                throw new BusinessException("Gift product ID is required for buy X get Y benefit");
            }

            if (buyQuantity == null || buyQuantity < 1) {
                throw new BusinessException("Buy quantity is required for buy X get Y benefit");
            }

            if (getQuantity == null || getQuantity < 1) {
                throw new BusinessException("Get quantity is required for buy X get Y benefit");
            }
        }
    }
}
