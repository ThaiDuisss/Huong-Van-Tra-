package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Cấu hình bán hàng của Mặt hàng, chỉ SUPER_ADMIN được thay đổi.
 */
public record SellableConfigRequest(
        @NotNull(message = "sellableQuantity is required")
        @DecimalMin(value = "0.00", message = "sellableQuantity must not be negative")
        BigDecimal sellableQuantity,
        @NotNull(message = "productionThreshold is required")
        @DecimalMin(value = "0.00", message = "productionThreshold must not be negative")
        BigDecimal productionThreshold,
        @NotNull(message = "productionBatchQuantity is required")
        @DecimalMin(value = "0.01", message = "productionBatchQuantity must be greater than 0")
        BigDecimal productionBatchQuantity
) {
}
