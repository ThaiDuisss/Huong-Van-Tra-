package org.hvtra.iventoryservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryBalanceResponse(
        UUID id,
        UUID inventoryItemId,
        UUID locationId,
        UUID branchId,
        BigDecimal quantityOnHand,
        BigDecimal quantityReserved,
        BigDecimal availableQuantity,
        Long version,
        Instant updatedAt
) {
}
