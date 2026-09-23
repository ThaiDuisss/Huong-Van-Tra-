package org.hvtra.iventoryservice.dashboard.dto.response;

import java.math.BigDecimal;

public record InventoryDashboardSummaryResponse(
        long skuCount,
        BigDecimal totalOnHand,
        BigDecimal totalAvailable,
        BigDecimal totalReserved,
        BigDecimal totalBlocked,
        BigDecimal totalInventoryValue,
        long belowReorderCount,
        long pendingDocumentsCount
) {}
