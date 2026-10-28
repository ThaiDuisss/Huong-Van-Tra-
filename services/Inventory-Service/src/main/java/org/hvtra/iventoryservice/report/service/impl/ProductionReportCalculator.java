package org.hvtra.iventoryservice.report.service.impl;

import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderLine;
import org.hvtra.iventoryservice.report.dto.ProductionWastageReportResponse;
import org.hvtra.iventoryservice.report.dto.ProductionWastageReportResponse.MaterialWastageDetail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tính báo cáo sản xuất từ lệnh sản xuất, không truy cập DB.
 *
 * <p>Mỗi dòng lệnh lưu {@code requiredQuantity = định mức × sản lượng × (1 + wasteRate%)} và
 * {@code consumedQuantity} là lượng thực xuất lúc duyệt. Định mức chuẩn được suy ngược bằng cách chia
 * cho {@code (1 + wasteRate%)}; hao hụt = thực xuất − định mức chuẩn, ngưỡng = wasteRate của BOM.
 */
final class ProductionReportCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Sai số làm tròn khi so tỷ lệ hao hụt với ngưỡng (điểm phần trăm). */
    private static final double THRESHOLD_TOLERANCE = 0.05;

    private ProductionReportCalculator() {
    }

    static ProductionWastageReportResponse calculate(
            List<ProductionOrder> approvedOrders,
            List<ProductionOrder> openOrders,
            Map<UUID, BigDecimal> availableByItemId,
            Map<UUID, String> unitSymbols
    ) {
        Map<UUID, MaterialTotals> materials = new LinkedHashMap<>();
        BigDecimal totalOutput = BigDecimal.ZERO;

        for (ProductionOrder order : approvedOrders) {
            totalOutput = totalOutput.add(nullToZero(order.getPlannedQuantity()));
            for (ProductionOrderLine line : order.getLines()) {
                InventoryItem ingredient = line.getIngredient();
                if (ingredient == null) {
                    continue;
                }
                BigDecimal wasteRate = nullToZero(line.getWasteRate());
                BigDecimal standard = standardQuantity(nullToZero(line.getRequiredQuantity()), wasteRate);
                materials.computeIfAbsent(ingredient.getId(), id -> new MaterialTotals(ingredient))
                        .add(order.getId(), standard, nullToZero(line.getConsumedQuantity()), wasteRate);
            }
        }

        List<MaterialWastageDetail> details = materials.values().stream()
                .map(totals -> totals.toDetail(totals.ingredient.getUnitId() != null
                        ? unitSymbols.get(totals.ingredient.getUnitId()) : null))
                .sorted(Comparator.comparing(MaterialWastageDetail::overThreshold).reversed()
                        .thenComparing(MaterialWastageDetail::wastageRate, Comparator.reverseOrder()))
                .toList();

        BigDecimal totalStandard = sum(details, MaterialWastageDetail::plannedQuantity);
        BigDecimal totalActual = sum(details, MaterialWastageDetail::actualQuantity);
        Shortage shortage = shortage(openOrders, availableByItemId);

        return ProductionWastageReportResponse.builder()
                .totalPlannedQuantity(totalStandard)
                .totalActualQuantity(totalActual)
                .totalWastageQuantity(sum(details, MaterialWastageDetail::wastageQuantity))
                .totalWastageValue(BigDecimal.ZERO)
                .completedOrderCount(approvedOrders.size())
                .totalOutputQuantity(totalOutput)
                .openOrderCount(openOrders.size())
                .shortageOrderCount(shortage.orderCount())
                .shortageMaterialCount(shortage.materialCount())
                .materials(details)
                .build();
    }

    /**
     * Lệnh thiếu = có ít nhất một nguyên liệu cần nhiều hơn tồn khả dụng. Nguyên liệu thiếu = tổng nhu cầu
     * của mọi lệnh đang mở vượt tồn khả dụng (các lệnh cùng tranh một nguồn tồn).
     */
    private static Shortage shortage(List<ProductionOrder> openOrders, Map<UUID, BigDecimal> availableByItemId) {
        Map<UUID, BigDecimal> demandByItemId = new HashMap<>();
        long orderCount = 0;

        for (ProductionOrder order : openOrders) {
            boolean orderShort = false;
            for (ProductionOrderLine line : order.getLines()) {
                if (line.getIngredient() == null) {
                    continue;
                }
                UUID itemId = line.getIngredient().getId();
                BigDecimal required = nullToZero(line.getRequiredQuantity());
                demandByItemId.merge(itemId, required, BigDecimal::add);
                if (required.compareTo(availableByItemId.getOrDefault(itemId, BigDecimal.ZERO)) > 0) {
                    orderShort = true;
                }
            }
            if (orderShort) {
                orderCount++;
            }
        }

        long materialCount = demandByItemId.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(
                        availableByItemId.getOrDefault(entry.getKey(), BigDecimal.ZERO)) > 0)
                .count();
        return new Shortage(orderCount, materialCount);
    }

    private static BigDecimal standardQuantity(BigDecimal required, BigDecimal wasteRate) {
        // Cùng cách làm tròn hệ số với lúc tạo lệnh (ProductionOrderServiceImpl).
        BigDecimal multiplier = BigDecimal.ONE.add(wasteRate.divide(HUNDRED, 4, RoundingMode.HALF_UP));
        return required.divide(multiplier, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(
            List<MaterialWastageDetail> details,
            Function<MaterialWastageDetail, BigDecimal> field
    ) {
        return details.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static double percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) {
            return 0.0;
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private record Shortage(long orderCount, long materialCount) {
    }

    private static final class MaterialTotals {
        private final InventoryItem ingredient;
        private final Set<UUID> orderIds = new HashSet<>();
        private BigDecimal standard = BigDecimal.ZERO;
        private BigDecimal actual = BigDecimal.ZERO;
        /** Tổng lượng hao hụt cho phép = Σ định mức × wasteRate%. */
        private BigDecimal allowance = BigDecimal.ZERO;

        private MaterialTotals(InventoryItem ingredient) {
            this.ingredient = ingredient;
        }

        private void add(UUID orderId, BigDecimal lineStandard, BigDecimal lineActual, BigDecimal wasteRate) {
            orderIds.add(orderId);
            standard = standard.add(lineStandard);
            actual = actual.add(lineActual);
            allowance = allowance.add(lineStandard.multiply(wasteRate).divide(HUNDRED, 4, RoundingMode.HALF_UP));
        }

        private MaterialWastageDetail toDetail(String unitSymbol) {
            BigDecimal wastage = actual.subtract(standard).max(BigDecimal.ZERO);
            double wastageRate = percent(wastage, standard);
            double thresholdRate = percent(allowance, standard);
            return new MaterialWastageDetail(
                    ingredient.getReferenceCode(),
                    ingredient.getName() != null ? ingredient.getName() : "Nguyên liệu",
                    unitSymbol,
                    standard,
                    actual,
                    wastage,
                    wastageRate,
                    thresholdRate,
                    orderIds.size(),
                    wastageRate > thresholdRate + THRESHOLD_TOLERANCE,
                    BigDecimal.ZERO
            );
        }
    }
}
