package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;

public record InventoryItemResponse(
        Long id,
        Long referenceId,
        InventoryItemType itemType,
        Long baseUnitId,
        InventoryStatus status
) {
}
