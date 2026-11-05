package org.hvtra.iventoryservice.movement.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;

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
        String branchName,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        List<LineResponse> lines
) {
    public InventoryTransactionResponse(
            UUID id,
            String transactionCode,
            InventoryTransactionType type,
            InventoryReferenceType referenceType,
            String referenceId,
            UUID branchId,
            UUID createdBy,
            Instant createdAt,
            List<LineResponse> lines
    ) {
        this(id, transactionCode, type, referenceType, referenceId, branchId, null, createdBy, null, createdAt, lines);
    }

    public record LineResponse(
            UUID id,
            UUID inventoryItemId,
            String itemCode,
            String itemName,
            UUID locationId,
            String locationCode,
            String locationName,
            BigDecimal quantity,
            BigDecimal beforeQuantity,
            BigDecimal afterQuantity
    ) {
        public LineResponse(
                UUID id,
                UUID inventoryItemId,
                UUID locationId,
                BigDecimal quantity,
                BigDecimal beforeQuantity,
                BigDecimal afterQuantity
        ) {
            this(id, inventoryItemId, null, null, locationId, null, null, quantity, beforeQuantity, afterQuantity);
        }
    }
}
