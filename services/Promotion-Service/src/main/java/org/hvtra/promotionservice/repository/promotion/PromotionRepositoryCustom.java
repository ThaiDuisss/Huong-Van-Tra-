package org.hvtra.promotionservice.repository.promotion;

import org.hvtra.promotionservice.dto.request.promotion.PromotionFilterRequest;
import org.hvtra.promotionservice.entity.Promotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PromotionRepositoryCustom {
    Page<Promotion> searchPromotions(PromotionFilterRequest request, Pageable pageable);
}
