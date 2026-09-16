package org.hvtra.iventoryservice.production.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CompleteProductionOrderRequest(
        @NotNull(message = "actualGoodQuantity is required")
        @DecimalMin(value = "0", message = "actualGoodQuantity must not be negative")
        BigDecimal actualGoodQuantity,

        @NotNull(message = "actualRejectedQuantity is required")
        @DecimalMin(value = "0", message = "actualRejectedQuantity must not be negative")
        BigDecimal actualRejectedQuantity,

        String note
) {
}
