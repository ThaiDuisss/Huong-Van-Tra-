package org.hvtra.iventoryservice.counting.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryTakeLineResponse(
        UUID id,
        UUID inventoryItemId,
        UUID locationId,
        UUID inventoryBatchId,
        String batchCode,
        LocalDate expiryDate,
        BigDecimal snapshotQuantity,
        BigDecimal countedQuantity,
        BigDecimal varianceQuantity,
        String reasonCode,
        String note
) {}
