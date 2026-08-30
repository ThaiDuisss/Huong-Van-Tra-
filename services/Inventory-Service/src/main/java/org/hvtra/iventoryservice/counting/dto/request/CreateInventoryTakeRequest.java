package org.hvtra.iventoryservice.counting.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountType;

import java.util.List;
import java.util.UUID;

public record CreateInventoryTakeRequest(
        @NotNull UUID branchId,
        UUID warehouseId,
        InventoryCountType countType,
        @NotNull InventoryCountScopeType scopeType,
        List<UUID> locationIds,
        List<UUID> inventoryItemIds,
        List<UUID> batchIds,
        List<UUID> assignedUserIds,
        UUID supervisorId,
        Boolean blindCount,
        String note
) {}
