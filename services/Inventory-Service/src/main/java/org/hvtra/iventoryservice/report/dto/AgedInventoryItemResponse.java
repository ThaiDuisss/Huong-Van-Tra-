package org.hvtra.iventoryservice.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AgedInventoryItemResponse(
        UUID batchId,
        String batchCode,
        UUID itemId,
        String skuCode,
        String itemName,
        UUID locationId,
        String locationName,
        BigDecimal quantityOnHand,
        BigDecimal unitCost,
        BigDecimal totalCostValue,
        Instant receivedAt,
        LocalDate expiryDate,
        long ageInDays,
        String agingBucket
) {}
