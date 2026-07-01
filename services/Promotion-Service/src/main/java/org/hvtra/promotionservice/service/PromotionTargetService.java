package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetCreateRequest;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionTargetResponse;

import java.util.UUID;

public interface PromotionTargetService {

    PromotionTargetResponse createTarget(UUID promotionId, PromotionTargetCreateRequest request);

    PageResponse<PromotionTargetResponse> getTargetsByPromotion(UUID promotionId, int page, int size);

    PromotionTargetResponse getTargetById(UUID promotionId, UUID targetId);

    PromotionTargetResponse updateTarget(UUID promotionId, UUID targetId, PromotionTargetUpdateRequest request);

    void deleteTarget(UUID promotionId, UUID targetId);
}