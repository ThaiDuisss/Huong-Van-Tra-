package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.shared.enums.InventoryItemType;
import org.hvtra.iventoryservice.shared.enums.InventoryStatus;

import java.util.UUID;

public record InventoryItemResponse(
        UUID id,
        UUID referenceId,
        InventoryItemType itemType,
        UUID unitId,
        InventoryStatus status,
        Integer reorderPoint
) {
}

