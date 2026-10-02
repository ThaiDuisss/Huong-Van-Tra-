package org.hvtra.iventoryservice.replenishment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ShelfReplenishmentLineResponse(
        UUID id,
        Integer lineNo,
        UUID inventoryItemId,
        UUID productVariantId,
        String sku,
        String itemName,
        UUID unitId,
        BigDecimal quantity,
        BigDecimal transferredQuantity,
        BigDecimal warehouseAvailable,
        BigDecimal shelfOnHand,
        String note
) {
}
