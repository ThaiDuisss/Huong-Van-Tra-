package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;

import java.util.UUID;

public record InventoryItemResponse(
        UUID id,
        Long referenceId,
        InventoryItemType itemType,
        Long baseUnitId,
        InventoryStatus status
) {
}
