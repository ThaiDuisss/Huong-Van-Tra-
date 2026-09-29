package org.hvtra.iventoryservice.reservation.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ReserveMaterialLineRequest(
        UUID orderItemId,
        @NotNull UUID productVariantId,
        @NotNull UUID ingredientId,
        UUID inventoryItemId,
        UUID locationId,
        @NotNull @DecimalMin("0.01") BigDecimal quantity
) {
}
