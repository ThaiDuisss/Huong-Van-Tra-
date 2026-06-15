package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.iventoryservice.enums.InventoryPolicy;
import org.hvtra.iventoryservice.enums.Status;

import java.math.BigDecimal;

public record ProductVariantRequest(
        @NotBlank @Size(max = 100) String sku,
        @NotBlank @Size(max = 255) String variantName,
        @NotNull @DecimalMin("0.00") BigDecimal sellingPrice,
        @NotNull @DecimalMin("0.00") BigDecimal costPrice,
        @NotNull InventoryPolicy inventoryPolicy,
        @NotNull Status status
) {
}
