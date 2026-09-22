package org.hvtra.promotionservice.validation.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionUpdateRequest;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionConditionValidator {

    ObjectMapper objectMapper;

    public void validateBeforeCreate(Promotion promotion, PromotionConditionCreateRequest request) {
        validatePromotionEditable(promotion);
        validateJsonConditionValue(request.getConditionValue());
    }

    public void validateBeforeUpdate(Promotion promotion, PromotionConditionUpdateRequest request) {
        validatePromotionEditable(promotion);
        validateJsonConditionValue(request.getConditionValue());
    }

    public void validateBeforeDelete(Promotion promotion) {
        validatePromotionEditable(promotion);
    }

    private void validatePromotionEditable(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.ACTIVE) {
            throw new BusinessException("Cannot modify conditions of active promotion");
        }

        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cannot modify conditions of cancelled promotion");
        }

        if (promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new BusinessException("Cannot modify conditions of expired promotion");
        }

        if (promotion.getStatus() == PromotionStatus.ENDED) {
            throw new BusinessException("Cannot modify conditions of ended promotion");
        }
    }

    private void validateJsonConditionValue(String conditionValue) {
        try {
            objectMapper.readTree(conditionValue);
        } catch (Exception ex) {
            throw new BusinessException("Condition value must be a valid JSON string");
        }
    }
}
