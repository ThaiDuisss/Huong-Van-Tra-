package org.hvtra.iventoryservice.goodsreceipt.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateGoodsReceiptRequest(
        @NotNull(message = "Supplier is required")
        UUID supplierId,

        @NotNull(message = "Location is required")
        UUID locationId,

        UUID branchId,

        Instant expectedDate,

        InventoryReferenceType referenceType,

        String referenceId,

        String note,

        @NotEmpty(message = "Receipt items cannot be empty")
        @Valid
        List<CreateGoodsReceiptRequest.LineItemRequest> items
) {
}
