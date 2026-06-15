package org.hvtra.iventoryservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryBalanceResponse(
        Long id,
        Long inventoryItemId,
        Long locationId,
        Long branchId,
        BigDecimal quantityOnHand,
        BigDecimal quantityReserved,
        BigDecimal availableQuantity,
        Long version,
        LocalDateTime updatedAt
) {
}
