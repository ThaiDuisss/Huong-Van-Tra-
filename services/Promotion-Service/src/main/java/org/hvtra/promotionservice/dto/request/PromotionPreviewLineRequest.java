package org.hvtra.promotionservice.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public class PromotionPreviewLineRequest {
    UUID productId;
    UUID categoryId;
    Integer quantity;
    BigDecimal lineAmount;
}
