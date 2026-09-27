package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SellableQuantityAdjustmentRequest(
        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.01", message = "quantity must be greater than 0")
        BigDecimal quantity,
        UUID locationId,
        String reason,
        String operationKey
) {
    public SellableQuantityAdjustmentRequest(BigDecimal quantity, UUID locationId, String reason) {
        this(quantity, locationId, reason, null);
    }
}
