package org.hvtra.iventoryservice.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpiringBatchItemResponse(
        UUID batchId,
        String batchCode,
        UUID itemId,
        String skuCode,
        String itemName,
        UUID locationId,
        String locationName,
        BigDecimal quantityOnHand,
        LocalDate expiryDate,
        long daysToExpiry,
        String status
) {}
