package org.hvtra.iventoryservice.report.service.impl;

import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderLine;
import org.hvtra.iventoryservice.report.dto.ProductionWastageReportResponse;
import org.hvtra.iventoryservice.report.dto.ProductionWastageReportResponse.MaterialWastageDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionReportCalculatorTest {

    private final InventoryItem tea = ingredient("NL-TRA", "Trà xanh");
    private final InventoryItem bag = ingredient("NL-TUI", "Túi lọc");

    @Test
    void approvedOrders_reportStandardVsActualAndOutput() {
        // Định mức 2 kg × 10 sp, hao hụt cho phép 5% → requiredQuantity 21; thực xuất đúng 21.
        ProductionOrder first = order(new BigDecimal("10"), line(tea, "21", "21", "5"));
        // Thực xuất 23 trên định mức 20 (cho phép 5% = 21) → vượt ngưỡng.
        ProductionOrder second = order(new BigDecimal("10"), line(tea, "21", "23", "5"), line(bag, "100", "100", "0"));

        ProductionWastageReportResponse report = ProductionReportCalculator.calculate(
                List.of(first, second), List.of(), Map.of(), Map.of(tea.getUnitId(), "kg"));

        assertThat(report.completedOrderCount()).isEqualTo(2);
        assertThat(report.totalOutputQuantity()).isEqualByComparingTo("20");

        MaterialWastageDetail teaRow = report.materials().getFirst();
        assertThat(teaRow.materialCode()).isEqualTo("NL-TRA");
        assertThat(teaRow.unitSymbol()).isEqualTo("kg");
        assertThat(teaRow.plannedQuantity()).isEqualByComparingTo("40");
        assertThat(teaRow.actualQuantity()).isEqualByComparingTo("44");
        assertThat(teaRow.wastageQuantity()).isEqualByComparingTo("4");
        assertThat(teaRow.wastageRate()).isEqualTo(10.0);
        assertThat(teaRow.thresholdRate()).isEqualTo(5.0);
        assertThat(teaRow.productionOrderCount()).isEqualTo(2);
        assertThat(teaRow.overThreshold()).isTrue();

        MaterialWastageDetail bagRow = report.materials().get(1);
        assertThat(bagRow.wastageQuantity()).isEqualByComparingTo("0");
        assertThat(bagRow.overThreshold()).isFalse();
        assertThat(report.totalWastageQuantity()).isEqualByComparingTo("4");
    }

    @Test
    void issuingExactlyTheBomAllowance_isNotOverThreshold() {
        ProductionOrder order = order(BigDecimal.ONE, line(tea, "1.05", "1.05", "5"));

        MaterialWastageDetail row = ProductionReportCalculator
                .calculate(List.of(order), List.of(), Map.of(), Map.of())
                .materials().getFirst();

        assertThat(row.wastageRate()).isEqualTo(5.0);
        assertThat(row.overThreshold()).isFalse();
        assertThat(row.unitSymbol()).isNull();
    }

    @Test
    void openOrders_countShortagePerOrderAndAcrossCompetingDemand() {
        // Mỗi lệnh cần 6 trà, tồn 10: từng lệnh đủ nhưng cộng lại thiếu; túi lọc thiếu ngay ở lệnh B.
        ProductionOrder a = order(BigDecimal.ONE, line(tea, "6", "0", "0"));
        ProductionOrder b = order(BigDecimal.ONE, line(tea, "6", "0", "0"), line(bag, "50", "0", "0"));

        ProductionWastageReportResponse report = ProductionReportCalculator.calculate(
                List.of(), List.of(a, b),
                Map.of(tea.getId(), new BigDecimal("10"), bag.getId(), new BigDecimal("20")), Map.of());

        assertThat(report.openOrderCount()).isEqualTo(2);
        assertThat(report.shortageOrderCount()).isEqualTo(1);
        assertThat(report.shortageMaterialCount()).isEqualTo(2);
        assertThat(report.materials()).isEmpty();
    }

    private static InventoryItem ingredient(String code, String name) {
        InventoryItem item = new InventoryItem();
        item.setId(UUID.randomUUID());
        item.setReferenceCode(code);
        item.setName(name);
        item.setUnitId(UUID.randomUUID());
        return item;
    }

    private static ProductionOrder order(BigDecimal plannedQuantity, ProductionOrderLine... lines) {
        ProductionOrder order = new ProductionOrder();
        order.setId(UUID.randomUUID());
        order.setPlannedQuantity(plannedQuantity);
        for (ProductionOrderLine line : lines) {
            order.addLine(line);
        }
        return order;
    }

    private static ProductionOrderLine line(InventoryItem ingredient, String required, String consumed, String wasteRate) {
        ProductionOrderLine line = new ProductionOrderLine();
        line.setIngredient(ingredient);
        line.setRequiredQuantity(new BigDecimal(required));
        line.setConsumedQuantity(new BigDecimal(consumed));
        line.setWasteRate(new BigDecimal(wasteRate));
        return line;
    }
}
