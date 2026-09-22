package org.hvtra.iventoryservice.integration.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderReturnItemResponse(
        UUID id,
        UUID orderItemId,
        UUID productVariantId,
        BigDecimal quantity,
        String reason,
        OrderReturnCondition condition,
        String note,
        String skuSnapshot,
        String productNameSnapshot,
        String variantNameSnapshot
) {
}
