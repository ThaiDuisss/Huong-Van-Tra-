package org.hvtra.iventoryservice.replenishmentpolicy.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReplenishmentPolicyResponse(
        UUID id,
        UUID inventoryItemId,
        String itemCode,
        String itemName,
        InventoryItemType itemType,
        InventoryLocationType locationType,
        BigDecimal minimumStock,
        BigDecimal maximumStock,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
