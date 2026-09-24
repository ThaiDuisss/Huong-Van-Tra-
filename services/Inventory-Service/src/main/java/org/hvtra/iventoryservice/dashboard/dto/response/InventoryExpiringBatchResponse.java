package org.hvtra.iventoryservice.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryExpiringBatchResponse(
        UUID id,
        String batchCode,
        UUID inventoryItemId,
        String skuCode,
        String productName,
        UUID locationId,
        String locationName,
        BigDecimal onHandQuantity,
        BigDecimal availableQuantity,
        String unit,
        LocalDate expiryDate,
        long daysRemaining,
        String urgency,
        String urgencyLabel,
        String status
) {}
