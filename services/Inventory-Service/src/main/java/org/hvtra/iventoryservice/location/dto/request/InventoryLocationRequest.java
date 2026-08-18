package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;

import java.util.UUID;

public record InventoryLocationRequest(
//        @NotNull UUID branchId,
        UUID parentLocationId,
        @NotBlank @Size(max = 255) String name,
        @NotNull InventoryLocationType type,
        @NotNull InventoryStatus status
) {
}
