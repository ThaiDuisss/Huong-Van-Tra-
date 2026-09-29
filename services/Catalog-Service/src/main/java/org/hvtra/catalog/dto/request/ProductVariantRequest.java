package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantRequest(
        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.01", message = "quantity must be greater than 0")
        BigDecimal quantity,
        @NotNull @DecimalMin("0.00") BigDecimal sellingPrice,
        @NotNull @DecimalMin("0.00") BigDecimal costPrice,
        @DecimalMin("0.00") BigDecimal sellableQuantity,
        @DecimalMin("0.00") BigDecimal productionThreshold,
        @DecimalMin("0.01") BigDecimal productionBatchQuantity,
        @NotNull UUID unitId,
        Integer reorderPoint,
        @NotNull InventoryPolicy inventoryPolicy,
        @NotNull Status status
) {
}
