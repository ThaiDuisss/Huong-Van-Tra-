package org.hvtra.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ManagementReportResponse {

    private ManagementReportResponse() {
    }

    public record BusinessReport(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate previousFromDate,
            LocalDate previousToDate,
            long totalOrders,
            BigDecimal totalRevenue,
            BigDecimal totalQuantitySold,
            BigDecimal averageOrderValue,
            long previousTotalOrders,
            BigDecimal previousTotalRevenue,
            BigDecimal previousTotalQuantitySold,
            BigDecimal revenueGrowthPercent,
            BigDecimal orderGrowthPercent,
            BigDecimal quantityGrowthPercent,
            List<RevenueTrendPoint> revenueTrend
    ) {
    }

    public record RevenueTrendPoint(
            LocalDate date,
            String label,
            BigDecimal revenue,
            BigDecimal previousRevenue,
            long orderCount,
            BigDecimal quantitySold
    ) {
    }

    public record ProductReport(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate previousFromDate,
            LocalDate previousToDate,
            List<ProductPerformanceItem> topSellingByQuantity,
            List<ProductPerformanceItem> topByRevenue,
            List<ProductPerformanceItem> fastGrowingProducts,
            List<ProductPerformanceItem> decliningProducts,
            List<ProductConsumptionTrendPoint> consumptionTrend
    ) {
    }

    public record ProductPerformanceItem(
            int rank,
            UUID productVariantId,
            String sku,
            String productName,
            String variantName,
            BigDecimal quantitySold,
            BigDecimal revenue,
            long orderCount,
            BigDecimal previousQuantitySold,
            BigDecimal previousRevenue,
            BigDecimal growthPercent
    ) {
    }

    public record ProductConsumptionTrendPoint(
            LocalDate date,
            String label,
            BigDecimal totalQuantity,
            BigDecimal totalRevenue
    ) {
    }

    public record CustomerReport(
            LocalDate fromDate,
            LocalDate toDate,
            long totalCustomers,
            long totalOrders,
            BigDecimal totalRevenue,
            BigDecimal averageOrdersPerCustomer,
            BigDecimal averageDaysBetweenOrders,
            BigDecimal returningCustomerRate,
            List<CustomerPerformanceItem> topByOrderCount,
            List<CustomerPerformanceItem> topByRevenue,
            List<OrderFrequencyBucket> frequencyDistribution,
            List<CustomerPurchaseHistoryItem> purchaseHistory
    ) {
    }

    public record CustomerPerformanceItem(
            int rank,
            UUID customerId,
            String customerName,
            String customerPhone,
            long orderCount,
            BigDecimal totalQuantity,
            BigDecimal totalRevenue,
            BigDecimal averageOrderValue,
            Instant lastOrderAt
    ) {
    }

    public record OrderFrequencyBucket(
            String label,
            long customerCount,
            BigDecimal percentage
    ) {
    }

    public record CustomerPurchaseHistoryItem(
            UUID orderId,
            String orderCode,
            UUID customerId,
            String customerName,
            String customerPhone,
            Instant createdAt,
            BigDecimal totalAmount,
            BigDecimal itemQuantity,
            String status,
            String paymentStatus
    ) {
    }

    public record ProductFormulaReport(
            LocalDate fromDate,
            LocalDate toDate,
            List<ProductFormulaItem> topProducts,
            PageResponse<ProductFormulaItem> items
    ) {
    }

    public record IngredientAnalysisReport(
            LocalDate fromDate,
            LocalDate toDate,
            List<IngredientFrequencyItem> topIngredients,
            List<IngredientPairItem> topPairs,
            PageResponse<IngredientFrequencyItem> items
    ) {
    }

    public record ProductFormulaItem(
            UUID variantId,
            UUID productId,
            String sku,
            String productName,
            String variantName,
            String categoryName,
            String formulaName,
            BigDecimal quantitySold,
            long orderCount,
            List<ProductFormulaIngredientItem> ingredients
    ) {
    }

    public record ProductFormulaIngredientItem(
            UUID ingredientId,
            String ingredientCode,
            String ingredientName,
            BigDecimal quantity,
            String unitSymbol,
            BigDecimal wasteRate,
            BigDecimal ratioPercent
    ) {
    }

    public record IngredientFrequencyItem(
            UUID ingredientId,
            String ingredientCode,
            String ingredientName,
            String unitSymbol,
            int formulaCount,
            BigDecimal weightedSalesVolume,
            BigDecimal averageRatioPercent
    ) {
    }

    public record IngredientPairItem(
            String pairKey,
            String ingredientA,
            String ingredientB,
            int coOccurrenceCount,
            BigDecimal combinedSalesVolume
    ) {
    }
}
