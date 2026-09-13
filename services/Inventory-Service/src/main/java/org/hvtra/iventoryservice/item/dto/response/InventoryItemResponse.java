package org.hvtra.iventoryservice.item.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;

import java.util.UUID;

public record InventoryItemResponse(
        UUID id,
        UUID referenceId,
        String referenceCode,
        String name,
        InventoryItemType itemType,
        UUID unitId,
        InventoryStatus status,
        Integer reorderPoint
) {
}
