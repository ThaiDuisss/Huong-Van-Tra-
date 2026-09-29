package org.hvtra.iventoryservice.document.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryDocumentResponse(
        UUID id,
        String documentCode,
        InventoryDocumentType documentType,
        InventoryDocumentStatus status,
        UUID branchId,
        UUID locationId,
        String locationName,
        String locationCode,
        UUID supplierId,
        String supplierName,
        InventoryReferenceType referenceType,
        String referenceId,
        Instant expectedDate,
        String note,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        String createdByName,
        UUID approvedBy,
        String approvedByName,
        Instant approvedAt,
        String approvalNote,
        UUID rejectedBy,
        String rejectedByName,
        Instant rejectedAt,
        String rejectionReason,
        UUID receivedBy,
        String receivedByName,
        Instant receivedAt,
        UUID qcBy,
        String qcByName,
        Instant qcAt,
        UUID postedBy,
        String postedByName,
        Instant postedAt,
        List<InventoryDocumentLineResponse> lines,
        long totalLines,
        java.math.BigDecimal totalOrderedQuantity,
        java.math.BigDecimal totalReceivedQuantity,
        java.math.BigDecimal totalAcceptedQuantity,
        java.math.BigDecimal totalRejectedQuantity
) {
}
