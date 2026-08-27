package org.hvtra.iventoryservice.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record InventoryBalanceViewResponse(
        UUID id,
        UUID inventoryBalanceId,
        UUID inventoryItemId,
        UUID referenceId,
        InventoryItemType itemType,
        String code,
        String name,
        UUID productId,
        String productCode,
        String productName,
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
        @JsonProperty("cost_price") BigDecimal costPrice,
        @JsonProperty("selling_price") BigDecimal sellingPrice,
        BigDecimal quantityOnHand,
        BigDecimal quantityReserved,
        BigDecimal availableQuantity,
        Integer reorderPoint,
        Instant updatedAt
) {
}
