package org.hvtra.iventoryservice.counting.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateInventoryCountsRequest(
        @NotNull @NotEmpty List<CountLineUpdate> lines
) {
    public record CountLineUpdate(
            @NotNull UUID lineId,
            @NotNull @PositiveOrZero BigDecimal countedQuantity,
            @PositiveOrZero BigDecimal goodQuantity,
            @PositiveOrZero BigDecimal damagedQuantity,
            @PositiveOrZero BigDecimal expiredQuantity,
            @PositiveOrZero BigDecimal quarantinedQuantity,
            String reasonCode,
            String note
    ) {}
}
