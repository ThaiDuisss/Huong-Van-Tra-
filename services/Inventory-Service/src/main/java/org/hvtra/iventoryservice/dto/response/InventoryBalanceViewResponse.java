package org.hvtra.iventoryservice.dto.response;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record InventoryBalanceViewResponse(
        UUID inventoryBalanceId,
        UUID inventoryItemId,
        UUID productId,
        String productCode,
        String productName,
        UUID variantId,
        String variantName,
        String sku,
        UUID categoryId,
        String categoryCode,
        String categoryName,
        UUID unitId,
        String unitName,
        String unitSymbol,
        UUID locationId,
        String locationName,
        UUID branchId,
        String imageUrl,
        @com.fasterxml.jackson.annotation.JsonProperty("cost_price") BigDecimal costPrice,
        @com.fasterxml.jackson.annotation.JsonProperty("selling_price") BigDecimal sellingPrice,
        BigDecimal quantityOnHand,
        BigDecimal quantityReserved,
        BigDecimal availableQuantity,
        Instant updatedAt
) {
}
