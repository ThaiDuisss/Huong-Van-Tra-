package org.hvtra.iventoryservice.production.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductionOrderRequest(
        @NotNull(message = "productVariantId is required")
        UUID productVariantId,

        UUID bomId,

        @NotNull(message = "plannedQuantity is required")
        @DecimalMin(value = "0.01", message = "plannedQuantity must be greater than 0")
        BigDecimal plannedQuantity,

        @NotNull(message = "locationId is required")
        UUID locationId,

        String note
) {
}
