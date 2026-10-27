package org.hvtra.iventoryservice.goodsreceipt.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateGoodsReceiptRequest(
        @NotNull(message = "Supplier is required")
        UUID supplierId,

        @NotNull(message = "Location (Warehouse) is required")
        UUID locationId,

        UUID branchId,

        Instant expectedDate,

        InventoryReferenceType referenceType,

        String referenceId,

        String note,

        @NotEmpty(message = "Receipt items cannot be empty")
        @Valid
        List<LineItemRequest> items
) {
    public record LineItemRequest(
            @NotNull(message = "Inventory item is required")
            UUID inventoryItemId,

            UUID unitId,

            @NotNull(message = "Ordered quantity is required")
            @Positive(message = "Ordered quantity must be greater than zero")
            BigDecimal orderedQuantity,

            String note
    ) {
    }
}
