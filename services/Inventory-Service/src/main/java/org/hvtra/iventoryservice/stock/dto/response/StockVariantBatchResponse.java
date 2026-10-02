package org.hvtra.iventoryservice.stock.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tồn của một mặt hàng tách theo nơi để: {@code shelfQuantity} là số đang trên kệ bán (SHELF),
 * {@code warehouseQuantity} là số còn trong kho (WAREHOUSE / STORAGE). {@code availableQuantity} là tổng hai phần.
 */
public record StockVariantBatchResponse(
        UUID variantId,
        UUID inventoryLocationId,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        String stockStatus,
        BigDecimal shelfQuantity,
        BigDecimal warehouseQuantity,
        UUID shelfLocationId,
        UUID warehouseLocationId
) {
    public static StockVariantBatchResponse empty(UUID variantId, UUID warehouseId) {
        return new StockVariantBatchResponse(variantId, warehouseId, BigDecimal.ZERO, BigDecimal.ZERO, "OUT_OF_STOCK",
                BigDecimal.ZERO, BigDecimal.ZERO, null, warehouseId);
    }
}
