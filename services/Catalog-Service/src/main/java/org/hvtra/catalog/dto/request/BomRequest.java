package org.hvtra.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BomRequest(
        @NotNull UUID productVariantId,
        @Size(max = 255) String name,
        @Size(max = 100) String bomCode,
        boolean isDefault,
        Integer version,
        @NotNull Status status,
        @Size(max = 1000) String description,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        @NotEmpty List<@Valid ItemRequest> items
) {
    public record ItemRequest(
            @NotNull UUID ingredientId,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal quantity,
            @NotNull UUID unitId,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal wasteRate
    ) {
    }
}

