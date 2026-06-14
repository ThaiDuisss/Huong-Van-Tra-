package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hvtra.iventoryservice.enums.InventoryLocationType;
import org.hvtra.iventoryservice.enums.InventoryStatus;

public record InventoryLocationRequest(
        @NotNull @Positive Long branchId,
        Long parentLocationId,
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull InventoryLocationType type,
        @NotNull InventoryStatus status
) {
}
