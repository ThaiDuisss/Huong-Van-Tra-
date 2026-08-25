package org.hvtra.iventoryservice.counting.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;

import java.util.List;
import java.util.UUID;

public record CreateInventoryTakeRequest(
        @NotNull UUID branchId,
        @NotNull InventoryCountScopeType scopeType,
        List<UUID> locationIds,
        List<UUID> inventoryItemIds,
        String note
) {}
