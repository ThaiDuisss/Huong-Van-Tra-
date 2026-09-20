package org.hvtra.iventoryservice.production.dto.response;

import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductionOrderResponse(
        UUID id,
        String orderCode,
        ProductionOrderStatus status,
        UUID productVariantId,
        BigDecimal plannedQuantity,
        UUID unitId,
        UUID locationId,
        String note,
        BigDecimal actualGoodQuantity,
        BigDecimal actualRejectedQuantity,
        UUID finishedBatchId,
        List<ProductionOrderLineResponse> lines,
        UUID submittedBy,
        Instant submittedAt,
        UUID approvedBy,
        Instant approvedAt,
        UUID releasedBy,
        Instant releasedAt,
        UUID startedBy,
        Instant startedAt,
        UUID completedBy,
        Instant completedAt,
        UUID cancelledBy,
        Instant cancelledAt,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt
) {
}
