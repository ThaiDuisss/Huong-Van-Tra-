package org.hvtra.iventoryservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record StockBatchResponse(
        UUID productId,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        String stockStatus
) {
}
