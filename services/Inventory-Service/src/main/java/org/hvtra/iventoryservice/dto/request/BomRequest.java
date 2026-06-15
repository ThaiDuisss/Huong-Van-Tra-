package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hvtra.iventoryservice.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BomRequest(
        @NotNull Long productVariantId,
        @NotNull @Min(1) Integer version,
        @NotNull Status status,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        @NotEmpty List<@Valid ItemRequest> items
) {
    public record ItemRequest(
            @NotNull Long ingredientId,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal quantity,
            @NotNull Long unitId,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal wasteRate
    ) {
    }
}
