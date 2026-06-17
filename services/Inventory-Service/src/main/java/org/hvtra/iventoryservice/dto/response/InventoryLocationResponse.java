package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.InventoryLocationType;
import org.hvtra.iventoryservice.enums.InventoryStatus;

public record InventoryLocationResponse(
        Long id,
        Long branchId,
        Long parentLocationId,
        String code,
        String name,
        InventoryLocationType type,
        InventoryStatus status
) {
}
