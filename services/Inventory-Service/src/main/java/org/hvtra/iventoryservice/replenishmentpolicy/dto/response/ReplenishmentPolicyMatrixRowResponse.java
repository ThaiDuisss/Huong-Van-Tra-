package org.hvtra.iventoryservice.replenishmentpolicy.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryItemType;

import java.math.BigDecimal;
import java.util.UUID;

public record ReplenishmentPolicyMatrixRowResponse(
        UUID inventoryItemId,
        String itemCode,
        String itemName,
        InventoryItemType itemType,
        PolicyCell warehousePolicy,
        PolicyCell shelfPolicy
) {
    public record PolicyCell(
            UUID id,
            BigDecimal minimumStock,
            BigDecimal maximumStock,
            Boolean enabled
    ) {
    }
}
