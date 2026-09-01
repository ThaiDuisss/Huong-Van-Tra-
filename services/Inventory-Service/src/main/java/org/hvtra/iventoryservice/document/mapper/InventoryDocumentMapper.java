package org.hvtra.iventoryservice.document.mapper;

import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentBatchAllocationResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentLineResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentResponse;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class InventoryDocumentMapper {

    public InventoryDocumentResponse toResponse(InventoryDocument document) {
        if (document == null) {
            return null;
        }

        List<InventoryDocumentLineResponse> lines = document.getLines() != null
                ? document.getLines().stream().map(this::toLineResponse).toList()
                : Collections.emptyList();

        BigDecimal totalOrdered = lines.stream()
                .map(InventoryDocumentLineResponse::orderedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReceived = lines.stream()
                .map(InventoryDocumentLineResponse::receivedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAccepted = lines.stream()
                .map(InventoryDocumentLineResponse::acceptedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRejected = lines.stream()
                .map(InventoryDocumentLineResponse::rejectedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String locationName = document.getLocation() != null ? document.getLocation().getName() : null;
        String locationCode = document.getLocation() != null ? document.getLocation().getCode() : null;

        return new InventoryDocumentResponse(
                document.getId(),
                document.getDocumentCode(),
                document.getDocumentType(),
                document.getStatus(),
                document.getBranchId(),
                document.getLocationId(),
                locationName,
                locationCode,
                document.getSupplierId(),
                null,
                document.getReferenceType(),
                document.getReferenceId(),
                document.getExpectedDate(),
                document.getNote(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getCreatedBy(),
                document.getApprovedBy(),
                document.getApprovedAt(),
                document.getApprovalNote(),
                document.getRejectedBy(),
                document.getRejectedAt(),
                document.getRejectionReason(),
                document.getReceivedBy(),
                document.getReceivedAt(),
                document.getQcBy(),
                document.getQcAt(),
                document.getPostedBy(),
                document.getPostedAt(),
                lines,
                lines.size(),
                totalOrdered,
                totalReceived,
                totalAccepted,
                totalRejected
        );
    }

    public InventoryDocumentLineResponse toLineResponse(InventoryDocumentLine line) {
        if (line == null) {
            return null;
        }

        List<InventoryDocumentBatchAllocationResponse> allocations = line.getBatchAllocations() != null
                ? line.getBatchAllocations().stream().map(this::toAllocationResponse).toList()
                : Collections.emptyList();

        String refCode = line.getInventoryItem() != null ? line.getInventoryItem().getReferenceCode() : null;
        String itemName = line.getInventoryItem() != null && line.getInventoryItem().getName() != null
                ? line.getInventoryItem().getName()
                : refCode;

        return new InventoryDocumentLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getInventoryItem() != null ? line.getInventoryItem().getId() : null,
                refCode,
                itemName,
                line.getUnitId(),
                null,
                line.getOrderedQuantity(),
                line.getReceivedQuantity(),
                line.getShortageQuantity(),
                line.getExcessQuantity(),
                line.getAcceptedQuantity(),
                line.getRejectedQuantity(),
                line.getNote(),
                allocations
        );
    }

    public InventoryDocumentBatchAllocationResponse toAllocationResponse(InventoryDocumentBatchAllocation allocation) {
        if (allocation == null) {
            return null;
        }

        var batch = allocation.getInventoryBatch();
        return new InventoryDocumentBatchAllocationResponse(
                allocation.getId(),
                batch != null ? batch.getId() : null,
                batch != null ? batch.getBatchCode() : null,
                batch != null ? batch.getSupplierBatchCode() : null,
                batch != null ? batch.getManufactureDate() : null,
                batch != null ? batch.getExpiryDate() : null,
                allocation.getQuantity()
        );
    }
}
