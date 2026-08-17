package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hvtra.iventoryservice.shared.enums.InventoryReferenceType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StoreStockOutRequest(
        @NotNull InventoryReferenceType referenceType,
        String referenceId,
        @NotEmpty List<@Valid LineRequest> lines
) {
    public record LineRequest(
            @NotNull UUID inventoryItemId,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal quantity
    ) {
    }
}
