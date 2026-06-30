package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantRequest(
        @NotBlank @Size(max = 255) String variantName,
        @NotNull @DecimalMin("0.00") BigDecimal sellingPrice,
        @NotNull @DecimalMin("0.00") BigDecimal costPrice,
        @NotNull UUID unitId,
        @NotNull InventoryPolicy inventoryPolicy,
        @NotNull Status status
) {
}
