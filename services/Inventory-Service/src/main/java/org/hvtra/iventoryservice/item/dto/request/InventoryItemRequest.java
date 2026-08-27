package org.hvtra.iventoryservice.item.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;

import java.util.UUID;

public record InventoryItemRequest(
        @NotNull UUID referenceId,
        String referenceCode,
        @NotNull InventoryItemType itemType,
        UUID unitId,
        @NotNull InventoryStatus status,
        Integer reorderPoint
) {
}
