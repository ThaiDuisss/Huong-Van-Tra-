package org.hvtra.iventoryservice.replenishmentpolicy.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;

import java.math.BigDecimal;
import java.util.UUID;

public record ReplenishmentPolicyRequest(
        @NotNull(message = "inventoryItemId is required")
        UUID inventoryItemId,

        @NotNull(message = "locationType is required")
        InventoryLocationType locationType,

        @NotNull(message = "minimumStock is required")
        @DecimalMin(value = "0.00", message = "minimumStock must not be negative")
        BigDecimal minimumStock,

        @DecimalMin(value = "0.00", message = "maximumStock must not be negative")
        BigDecimal maximumStock,

        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}
