package org.hvtra.iventoryservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record StockVariantBatchResponse(
        UUID variantId,
        UUID inventoryLocationId,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        String stockStatus
) {
}
