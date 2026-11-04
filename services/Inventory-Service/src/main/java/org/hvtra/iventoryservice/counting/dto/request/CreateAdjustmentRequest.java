package org.hvtra.iventoryservice.counting.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateAdjustmentRequest(
        @NotNull UUID branchId,
        String reasonCode,
        String note,
        @NotEmpty @Valid List<AdjustmentLineRequest> lines
) {
    public record AdjustmentLineRequest(
            @NotNull UUID inventoryItemId,
            @NotNull UUID locationId,
            @NotNull BigDecimal adjustmentQuantity,
            String note
    ) {}
}
