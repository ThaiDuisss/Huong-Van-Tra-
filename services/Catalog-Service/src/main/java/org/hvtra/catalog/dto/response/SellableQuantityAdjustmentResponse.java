package org.hvtra.catalog.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record SellableQuantityAdjustmentResponse(
        UUID productVariantId,
        BigDecimal requestedQuantity,
        BigDecimal oldSellableQuantity,
        BigDecimal newSellableQuantity,
        boolean thresholdCrossed,
        BigDecimal productionThreshold,
        BigDecimal productionBatchQuantity,
        UUID locationId,
        String reason
) {
}
