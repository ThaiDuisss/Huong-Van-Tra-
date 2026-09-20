package org.hvtra.iventoryservice.report.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record InventoryValueReportResponse(
        BigDecimal totalValue,
        BigDecimal totalOnHandQuantity,
        long totalSkuCount,
        List<CategoryValueSummary> categoryBreakdown,
        List<LocationValueSummary> locationBreakdown,
        List<ItemValueDetail> topValuedItems
) {
    public record CategoryValueSummary(
            String categoryCode,
            String categoryName,
            long skuCount,
            BigDecimal totalQuantity,
            BigDecimal totalValue,
            double percentage
    ) {}

    public record LocationValueSummary(
            UUID locationId,
            String locationCode,
            String locationName,
            BigDecimal totalQuantity,
            BigDecimal totalValue
    ) {}

    public record ItemValueDetail(
            UUID inventoryItemId,
            String code,
            String name,
            String categoryName,
            String locationName,
            String unitSymbol,
            BigDecimal quantityOnHand,
            BigDecimal costPrice,
            BigDecimal totalValue
    ) {}
}
