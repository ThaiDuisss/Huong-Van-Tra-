package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;

public record InventoryItemRequest(
        @NotNull @Positive Long referenceId,
        @NotNull InventoryItemType itemType,
        @NotNull @Positive Long baseUnitId,
        @NotNull InventoryStatus status
) {
}
