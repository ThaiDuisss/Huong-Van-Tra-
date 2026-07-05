package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.PromotionPreviewRequest;
import org.hvtra.promotionservice.dto.response.PromotionPreviewResponse;

public interface PromotionApplyService {

    PromotionPreviewResponse preview(PromotionPreviewRequest request);

}
