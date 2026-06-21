package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.enums.InventoryTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryTransactionResponse(
        UUID id,
        String transactionCode,
        InventoryTransactionType type,
        InventoryReferenceType referenceType,
        String referenceId,
        UUID branchId,
        String createdBy,
        Instant createdAt,
        List<LineResponse> lines
) {
    public record LineResponse(
            UUID id,
            UUID inventoryItemId,
            UUID locationId,
            BigDecimal quantity,
            BigDecimal beforeQuantity,
            BigDecimal afterQuantity
    ) {
    }
}
