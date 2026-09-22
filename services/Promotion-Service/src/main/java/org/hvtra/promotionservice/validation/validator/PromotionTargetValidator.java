package org.hvtra.promotionservice.validation.validator;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetCreateRequest;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetUpdateRequest;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.repository.promotion.PromotionTargetRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionTargetValidator {

    PromotionTargetRepository promotionTargetRepository;

    public void validateBeforeCreate(
            Promotion promotion,
            PromotionTargetCreateRequest request
    ) {
        validatePromotionEditable(promotion);
        validateDuplicateTarget(promotion.getId(), request);
        validateOverrideConfig(
                request.getOverrideBenefitType(),
                request.getOverrideValue(),
                request.getOverrideMaxDiscount()
        );
    }

    public void validateBeforeUpdate(
            Promotion promotion,
            UUID targetId,
            PromotionTargetUpdateRequest request
    ) {
        validatePromotionEditable(promotion);
        validateDuplicateTargetOnUpdate(promotion.getId(), targetId, request);
        validateOverrideConfig(
                request.getOverrideBenefitType(),
                request.getOverrideValue(),
                request.getOverrideMaxDiscount()
        );
    }

    public void validateBeforeDelete(Promotion promotion) {
        validatePromotionEditable(promotion);
    }

    private void validatePromotionEditable(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.ACTIVE) {
            throw new BusinessException("Cannot modify targets of active promotion");
        }

        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cannot modify targets of cancelled promotion");
        }

        if (promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new BusinessException("Cannot modify targets of expired promotion");
        }

        if (promotion.getStatus() == PromotionStatus.ENDED) {
            throw new BusinessException("Cannot modify targets of ended promotion");
        }
    }

    private void validateDuplicateTarget(
            UUID promotionId,
            PromotionTargetCreateRequest request
    ) {
        boolean exists = promotionTargetRepository.existsByPromotionIdAndTargetTypeAndTargetIdAndIsDeletedFalse(
                promotionId,
                request.getTargetType(),
                request.getTargetId()
        );

        if (exists) {
            throw new BusinessException("Promotion target already exists");
        }
    }

    private void validateDuplicateTargetOnUpdate(
            UUID promotionId,
            UUID currentTargetId,
            PromotionTargetUpdateRequest request
    ) {
        boolean exists = promotionTargetRepository.existsByPromotionIdAndTargetTypeAndTargetIdAndIdNotAndIsDeletedFalse(
                promotionId,
                request.getTargetType(),
                request.getTargetId(),
                currentTargetId
        );

        if (exists) {
            throw new BusinessException("Promotion target already exists");
        }
    }

    private void validateOverrideConfig(
            BenefitType overrideBenefitType,
            BigDecimal overrideValue,
            BigDecimal overrideMaxDiscount
    ) {
        if (overrideBenefitType == null) {
            if (overrideValue != null || overrideMaxDiscount != null) {
                throw new BusinessException("Override benefit type is required when override value or max discount is provided");
            }

            return;
        }

        if (overrideValue == null) {
            throw new BusinessException("Override value is required when override benefit type is provided");
        }

        if (overrideBenefitType == BenefitType.PERCENT_DISCOUNT
                && overrideValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Override percent discount must not exceed 100");
        }
    }
}
