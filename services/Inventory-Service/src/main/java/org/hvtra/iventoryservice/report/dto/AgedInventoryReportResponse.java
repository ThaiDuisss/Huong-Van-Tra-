package org.hvtra.iventoryservice.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record AgedInventoryReportResponse(
        List<AgedInventoryItemResponse> items,
        long totalItemsCount,
        BigDecimal totalAgedCostValue,
        long itemsOver30DaysCount,
        long itemsOver60DaysCount,
        long itemsOver90DaysCount
) {}
