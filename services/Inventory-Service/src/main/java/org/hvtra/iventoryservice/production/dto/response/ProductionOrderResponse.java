package org.hvtra.iventoryservice.production.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        String productVariantName,
        BigDecimal plannedQuantity,
        UUID unitId,
        UUID locationId,
        String note,
        List<ProductionOrderLineResponse> lines,
        UUID createdBy,
        String createByName,
        UUID submittedBy,
        Instant submittedAt,
        UUID approvedBy,
        Instant approvedAt,
        UUID rejectedBy,
        Instant rejectedAt,
        UUID cancelledBy,
        Instant cancelledAt,
        String cancelReason,
        Instant expectedCompletionDate,
        Instant createdAt,
        Instant updatedAt
) {
    @JsonProperty("createBy")
    public UUID createBy() {
        return createdBy;
    }

    @JsonProperty("createdByName")
    public String createdByName() {
        return createByName;
    }

    public ProductionOrderResponse(
            UUID id,
            String orderCode,
            ProductionOrderStatus status,
            UUID productVariantId,
            String productVariantName,
            BigDecimal plannedQuantity,
            UUID unitId,
            UUID locationId,
            String note,
            List<ProductionOrderLineResponse> lines,
            UUID createdBy,
            UUID submittedBy,
            Instant submittedAt,
            UUID approvedBy,
            Instant approvedAt,
            UUID rejectedBy,
            Instant rejectedAt,
            UUID cancelledBy,
            Instant cancelledAt,
            String cancelReason,
            Instant expectedCompletionDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, orderCode, status, productVariantId, productVariantName, plannedQuantity, unitId, locationId, note, lines,
                createdBy, null, submittedBy, submittedAt, approvedBy, approvedAt, rejectedBy, rejectedAt, cancelledBy, cancelledAt,
                cancelReason, expectedCompletionDate, createdAt, updatedAt);
    }

    public ProductionOrderResponse(
            UUID id,
            String orderCode,
            ProductionOrderStatus status,
            UUID productVariantId,
            String productVariantName,
            BigDecimal plannedQuantity,
            UUID unitId,
            UUID locationId,
            String note,
            List<ProductionOrderLineResponse> lines,
            UUID submittedBy,
            Instant submittedAt,
            UUID approvedBy,
            Instant approvedAt,
            UUID rejectedBy,
            Instant rejectedAt,
            UUID cancelledBy,
            Instant cancelledAt,
            String cancelReason,
            Instant expectedCompletionDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, orderCode, status, productVariantId, productVariantName, plannedQuantity, unitId, locationId, note, lines,
                submittedBy, null, submittedBy, submittedAt, approvedBy, approvedAt, rejectedBy, rejectedAt, cancelledBy, cancelledAt,
                cancelReason, expectedCompletionDate, createdAt, updatedAt);
    }

    public ProductionOrderResponse(
            UUID id,
            String orderCode,
            ProductionOrderStatus status,
            UUID productVariantId,
            BigDecimal plannedQuantity,
            UUID unitId,
            UUID locationId,
            String note,
            List<ProductionOrderLineResponse> lines,
            UUID submittedBy,
            Instant submittedAt,
            UUID approvedBy,
            Instant approvedAt,
            UUID rejectedBy,
            Instant rejectedAt,
            UUID cancelledBy,
            Instant cancelledAt,
            String cancelReason,
            Instant expectedCompletionDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, orderCode, status, productVariantId, null, plannedQuantity, unitId, locationId, note, lines,
                submittedBy, null, submittedBy, submittedAt, approvedBy, approvedAt, rejectedBy, rejectedAt, cancelledBy, cancelledAt,
                cancelReason, expectedCompletionDate, createdAt, updatedAt);
    }
}
