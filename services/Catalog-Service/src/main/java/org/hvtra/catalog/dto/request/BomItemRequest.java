package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record BomItemRequest(
        @NotNull(message = "ingredientId is required")
        UUID ingredientId,

        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.01", message = "quantity must be greater than 0")
        BigDecimal quantity,

        @NotNull(message = "unitId is required")
        UUID unitId,

        @NotNull(message = "wasteRate is required")
        @DecimalMin(value = "0", message = "wasteRate must not be negative")
        BigDecimal wasteRate
) {
}
