package org.hvtra.iventoryservice.stock.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryBalanceResponse(
        UUID id,
        UUID inventoryBatchId,
        UUID inventoryItemId,
        String batchCode,
        UUID locationId,
        UUID branchId,
        BigDecimal quantityOnHand,
        BigDecimal quantityReserved,
        BigDecimal quantityBlocked,
        BigDecimal availableQuantity,
        Long version,
        Instant updatedAt
) {
}
