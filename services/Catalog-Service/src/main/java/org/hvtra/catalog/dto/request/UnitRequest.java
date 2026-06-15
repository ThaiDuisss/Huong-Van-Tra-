package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.*;
import org.hvtra.catalog.enums.Status;

public record UnitRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String symbol,
        @NotNull @Min(0) @Max(10) Integer precisionScale,
        @NotNull Status status
) {
}
