package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hvtra.iventoryservice.enums.InventoryReferenceType;

import java.math.BigDecimal;
import java.util.List;

public record StockInRequest(
        @NotNull @Positive Long branchId,
        @NotNull InventoryReferenceType referenceType,
        Long referenceId,
        @NotEmpty List<@Valid LineRequest> lines
) {
    public record LineRequest(
            @NotNull @Positive Long inventoryItemId,
            @NotNull @Positive Long locationId,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal quantity
    ) {
    }
}
