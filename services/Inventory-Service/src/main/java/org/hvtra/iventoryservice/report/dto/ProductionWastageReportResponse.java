package org.hvtra.iventoryservice.report.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record ProductionWastageReportResponse(
        BigDecimal totalPlannedQuantity,
        BigDecimal totalActualQuantity,
        BigDecimal totalWastageQuantity,
        BigDecimal totalWastageValue,
        List<MaterialWastageDetail> materials
) {
    public record MaterialWastageDetail(
            String materialCode,
            String materialName,
            String unitSymbol,
            BigDecimal plannedQuantity,
            BigDecimal actualQuantity,
            BigDecimal wastageQuantity,
            double wastageRate,
            BigDecimal estimatedLossValue
    ) {}
}
