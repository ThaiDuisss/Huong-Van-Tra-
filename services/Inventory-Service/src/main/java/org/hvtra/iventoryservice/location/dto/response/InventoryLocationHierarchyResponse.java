package org.hvtra.iventoryservice.location.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;

import java.util.UUID;

public record InventoryLocationHierarchyResponse(
        UUID id,
        UUID branchId,
        UUID parentLocationId,
        String parentLocationName,
        String parentLocationCode,
        String code,
        String name,
        InventoryLocationType type,
        InventoryStatus status,
        int level,
        int tier,
        boolean hasChildren,
        int childrenCount
) {
}
