package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.*;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String symbol,
        @NotNull @Min(0) @Max(10) Integer precisionScale,
        @NotNull UnitType type,
        UUID baseUnitId,
        BigDecimal conversionRate,
        @NotNull Status status
) {
}

