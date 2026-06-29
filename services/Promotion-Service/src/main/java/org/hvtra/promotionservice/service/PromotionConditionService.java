package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionConditionResponse;

import java.util.UUID;

public interface PromotionConditionService {

    PromotionConditionResponse createCondition(
            UUID promotionId,
            PromotionConditionCreateRequest request
    );

    PageResponse<PromotionConditionResponse> getConditionsByPromotion(
            UUID promotionId,
            int page,
            int size
    );

    PromotionConditionResponse getConditionById(
            UUID promotionId,
            UUID conditionId
    );

    PromotionConditionResponse updateCondition(
            UUID promotionId,
            UUID conditionId,
            PromotionConditionUpdateRequest request
    );

    void deleteCondition(
            UUID promotionId,
            UUID conditionId
    );
}