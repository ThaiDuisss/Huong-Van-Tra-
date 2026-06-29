package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitCreateRequest;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionBenefitResponse;

import java.util.UUID;

public interface PromotionBenefitService {

    PromotionBenefitResponse createBenefit(
            UUID promotionId,
            PromotionBenefitCreateRequest request
    );

    PageResponse<PromotionBenefitResponse> getBenefitsByPromotion(
            UUID promotionId,
            int page,
            int size
    );

    PromotionBenefitResponse getBenefitById(
            UUID promotionId,
            UUID benefitId
    );

    PromotionBenefitResponse updateBenefit(
            UUID promotionId,
            UUID benefitId,
            PromotionBenefitUpdateRequest request
    );

    void deleteBenefit(
            UUID promotionId,
            UUID benefitId
    );
}
