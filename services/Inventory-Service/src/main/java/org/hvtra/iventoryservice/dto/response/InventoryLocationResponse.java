package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.shared.enums.InventoryLocationType;
import org.hvtra.iventoryservice.shared.enums.InventoryStatus;

import java.util.UUID;

public record InventoryLocationResponse(
        UUID id,
        UUID branchId,
        UUID parentLocationId,
        String code,
        String name,
        InventoryLocationType type,
        InventoryStatus status
) {
}
