package org.hvtra.iventoryservice.availability.dto;

import org.hvtra.iventoryservice.availability.AvailabilityScenario;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kết quả ước tính ngày có hàng cho từng mặt hàng trong đơn.
 *
 * @param backorder            true nếu có ít nhất một mặt hàng phải chờ
 * @param estimatedAvailableAt thời điểm muộn nhất trong các mặt hàng phải chờ (null nếu không ước tính được)
 */
public record AvailabilityEstimateResponse(
        boolean backorder,
        Instant estimatedAvailableAt,
        List<LineEstimate> lines
) {
    /**
     * @param durationSource nguồn của thời gian sản xuất: VARIANT_HISTORY, ALL_HISTORY hoặc DEFAULT
     * @param productionOrderCode lệnh sản xuất đang chạy (IN_PRODUCTION) hoặc lệnh vừa tạo theo đơn
     * @param depositRecommended nên thu cọc thay vì thu đủ (khi còn phải chờ nhập nguyên liệu)
     */
    public record LineEstimate(
            UUID productVariantId,
            String itemName,
            BigDecimal requestedQuantity,
            BigDecimal shelfQuantity,
            BigDecimal warehouseQuantity,
            BigDecimal missingQuantity,
            AvailabilityScenario scenario,
            Instant estimatedAvailableAt,
            Integer estimatedWaitDays,
            BigDecimal productionDays,
            String durationSource,
            String productionOrderCode,
            boolean depositRecommended,
            List<MaterialEstimate> materials,
            String message
    ) {
        public LineEstimate withProductionOrderCode(String code) {
            return new LineEstimate(productVariantId, itemName, requestedQuantity, shelfQuantity, warehouseQuantity,
                    missingQuantity, scenario, estimatedAvailableAt, estimatedWaitDays, productionDays,
                    durationSource, code, depositRecommended, materials, message);
        }
    }

    /**
     * @param arrivalSource IN_STOCK, OPEN_RECEIPT, RECEIPT_HISTORY hoặc DEFAULT
     */
    public record MaterialEstimate(
            UUID ingredientId,
            String name,
            BigDecimal requiredQuantity,
            BigDecimal availableQuantity,
            BigDecimal missingQuantity,
            Instant expectedArrivalAt,
            String arrivalSource,
            String receiptCode
    ) {}
}
