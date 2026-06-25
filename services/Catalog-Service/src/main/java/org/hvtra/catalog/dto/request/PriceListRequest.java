package org.hvtra.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PriceListRequest(
        @NotNull UUID branchId,
        @NotBlank @Size(max = 255) String name,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        @NotNull Status status,
        @NotEmpty List<@Valid ItemRequest> items
) {
    public record ItemRequest(
            @NotNull UUID productVariantId,
            @NotNull @DecimalMin("0.00") BigDecimal sellingPrice
    ) {
    }
}

