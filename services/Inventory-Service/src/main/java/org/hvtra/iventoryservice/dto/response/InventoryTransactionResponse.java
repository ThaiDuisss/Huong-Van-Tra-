package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.enums.InventoryTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InventoryTransactionResponse(
        Long id,
        String transactionCode,
        InventoryTransactionType type,
        InventoryReferenceType referenceType,
        Long referenceId,
        Long branchId,
        Long createdBy,
        LocalDateTime createdAt,
        List<LineResponse> lines
) {
    public record LineResponse(
            Long id,
            Long inventoryItemId,
            Long locationId,
            BigDecimal quantity,
            BigDecimal beforeQuantity,
            BigDecimal afterQuantity
    ) {
    }
}
