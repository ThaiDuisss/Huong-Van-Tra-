package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionFilterRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;

import java.util.UUID;

public interface PromotionService {

    PromotionResponse createPromotion(PromotionCreateRequest request);

    PromotionResponse createPromotionByEvent(UUID eventId, PromotionCreateRequest request);

    PromotionResponse getPromotionById(UUID promotionId);

    PageResponse<PromotionResponse> getPromotions(PromotionFilterRequest request, int page, int size);

    PageResponse<PromotionResponse> getPromotionsByEvent(UUID eventId, PromotionFilterRequest request, int page, int size);

    PromotionResponse updatePromotion(UUID promotionId, PromotionUpdateRequest request);

    void deletePromotion(UUID promotionId);

    PromotionResponse schedulePromotion(UUID promotionId);

    PromotionResponse activatePromotion(UUID promotionId);

    PromotionResponse pausePromotion(UUID promotionId);

    PromotionResponse resumePromotion(UUID promotionId);

    PromotionResponse cancelPromotion(UUID promotionId);

    PromotionResponse expirePromotion(UUID promotionId);

    PromotionResponse duplicatePromotion(UUID promotionId);
}
