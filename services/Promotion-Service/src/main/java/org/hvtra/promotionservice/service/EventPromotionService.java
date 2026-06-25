package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.eventpromotion.EventPromotionRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;

import java.util.UUID;

public interface EventPromotionService {

    PageResponse<PromotionResponse> getPromotionsByEvent(
            UUID eventId,
            int page,
            int size
    );

    PromotionResponse createPromotionInEvent(
            UUID eventId,
            PromotionCreateRequest request
    );

    void attachPromotionsToEvent(
            UUID eventId,
            EventPromotionRequest request
    );

    void detachPromotionsFromEvent(
            UUID eventId,
            EventPromotionRequest request
    );

}
