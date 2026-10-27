package org.hvtra.iventoryservice.bundle.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tồn nguyên liệu cho một bundle tại kho giữ chỗ của chi nhánh.
 *
 * @param enough               đủ mọi nguyên liệu, có thể giữ chỗ ngay
 * @param estimatedAvailableAt ngày muộn nhất trong các nguyên liệu còn thiếu (null nếu đủ)
 */
public record CustomBundleAvailabilityResponse(
        boolean enough,
        Instant estimatedAvailableAt,
        Integer estimatedWaitDays,
        List<MaterialLine> lines
) {
    /** @param arrivalSource IN_STOCK, OPEN_RECEIPT, RECEIPT_HISTORY hoặc DEFAULT */
    public record MaterialLine(
            UUID ingredientId,
            String ingredientCode,
            String ingredientName,
            BigDecimal requiredQuantity,
            BigDecimal availableQuantity,
            BigDecimal missingQuantity,
            Instant expectedArrivalAt,
            String arrivalSource,
            String receiptCode
    ) {
    }
}
