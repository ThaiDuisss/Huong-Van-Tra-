package org.hvtra.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PriceListRequest(
        @NotNull Long branchId,
        @NotBlank @Size(max = 255) String name,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        @NotNull Status status,
        @NotEmpty List<@Valid ItemRequest> items
) {
    public record ItemRequest(
            @NotNull Long productVariantId,
            @NotNull @DecimalMin("0.00") BigDecimal sellingPrice
    ) {
    }
}
