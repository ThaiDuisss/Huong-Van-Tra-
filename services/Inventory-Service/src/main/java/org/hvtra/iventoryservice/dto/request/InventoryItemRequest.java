package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;

import java.util.UUID;

public record InventoryItemRequest(
        @NotNull UUID referenceId,
        @NotNull InventoryItemType itemType,
        @NotNull UUID baseUnitId,
        @NotNull InventoryStatus status
) {
}

