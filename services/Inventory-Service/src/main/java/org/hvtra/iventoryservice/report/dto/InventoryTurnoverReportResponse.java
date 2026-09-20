package org.hvtra.iventoryservice.report.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record InventoryTurnoverReportResponse(
        int periodDays,
        double averageTurnoverRate,
        double averageDsi,
        long slowMovingSkuCount,
        List<TurnoverItemDetail> items
) {
    public record TurnoverItemDetail(
            UUID inventoryItemId,
            String code,
            String name,
            String categoryName,
            String unitSymbol,
            BigDecimal averageStock,
            BigDecimal totalSoldQuantity,
            BigDecimal totalCogs,
            double turnoverRate,
            double dsi,
            String abcCategory
    ) {}
}
