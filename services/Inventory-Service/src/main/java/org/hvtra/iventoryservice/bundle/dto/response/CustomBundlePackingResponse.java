package org.hvtra.iventoryservice.bundle.dto.response;

import org.hvtra.iventoryservice.domain.enums.CustomBundlePackingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code lines} rỗng ở danh sách phân trang, đầy đủ ở chi tiết. */
public record CustomBundlePackingResponse(
        UUID id,
        String packingCode,
        UUID orderId,
        String orderCode,
        UUID orderItemId,
        String bundleName,
        UUID branchId,
        UUID locationId,
        CustomBundlePackingStatus status,
        BigDecimal expectedTotalWeight,
        BigDecimal actualTotalWeight,
        String packagingType,
        Integer numberOfPackages,
        String packingNote,
        Instant expectedAvailableAt,
        Instant reservedAt,
        UUID packedBy,
        Instant packedAt,
        String issueTransactionCode,
        Instant cancelledAt,
        String cancelReason,
        Instant createdAt,
        List<Line> lines
) {
    public record Line(
            UUID id,
            UUID ingredientId,
            String ingredientCode,
            String ingredientName,
            BigDecimal requiredQuantity,
            boolean picked
    ) {
    }
}
