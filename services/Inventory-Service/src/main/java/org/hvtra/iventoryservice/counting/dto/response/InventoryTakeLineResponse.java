package org.hvtra.iventoryservice.counting.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

public record InventoryTakeLineResponse(
        UUID id,
        UUID inventoryItemId,
        UUID locationId,
        UUID inventoryBatchId,
        String batchCode,
        LocalDate expiryDate,
        BigDecimal snapshotQuantity,
        BigDecimal movementQuantity,
        BigDecimal expectedQuantity,
        BigDecimal snapshotUnitCost,
        boolean counted,
        BigDecimal countedQuantity,
        BigDecimal varianceQuantity,
        BigDecimal varianceValue,
        BigDecimal goodQuantity,
        BigDecimal damagedQuantity,
        BigDecimal expiredQuantity,
        BigDecimal quarantinedQuantity,
        String reasonCode,
        String note,
        List<AttemptSummary> attempts
) {
    public record AttemptSummary(
            UUID id,
            Integer attemptNo,
            UUID counterId,
            BigDecimal countedQuantity,
            String note,
            Instant submittedAt
    ) {}
}
