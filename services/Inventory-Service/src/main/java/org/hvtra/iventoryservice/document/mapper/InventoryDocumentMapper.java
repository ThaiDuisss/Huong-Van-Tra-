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
import java.util.Map;
import java.util.UUID;

@Component
public class InventoryDocumentMapper {

    public InventoryDocumentResponse toResponse(InventoryDocument document) {
        return toResponse(document, null);
    }

    public InventoryDocumentResponse toResponse(InventoryDocument document, String supplierName) {
        return toResponse(document, supplierName, Map.of());
    }

    /** @param userNames tên người dùng theo Keycloak subject (createdBy, approvedBy...), thiếu thì để null */
    public InventoryDocumentResponse toResponse(InventoryDocument document, String supplierName, Map<UUID, String> userNames) {
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
                supplierName,
                document.getReferenceType(),
                document.getReferenceId(),
                document.getExpectedDate(),
                document.getNote(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getCreatedBy(),
                nameOf(userNames, document.getCreatedBy()),
                document.getApprovedBy(),
                nameOf(userNames, document.getApprovedBy()),
                document.getApprovedAt(),
                document.getApprovalNote(),
                document.getRejectedBy(),
                nameOf(userNames, document.getRejectedBy()),
                document.getRejectedAt(),
                document.getRejectionReason(),
                document.getReceivedBy(),
                nameOf(userNames, document.getReceivedBy()),
                document.getReceivedAt(),
                document.getQcBy(),
                nameOf(userNames, document.getQcBy()),
                document.getQcAt(),
                document.getPostedBy(),
                nameOf(userNames, document.getPostedBy()),
                document.getPostedAt(),
                lines,
                lines.size(),
                totalOrdered,
                totalReceived,
                totalAccepted,
                totalRejected
        );
    }

    private static String nameOf(Map<UUID, String> userNames, UUID userId) {
        return userId != null ? userNames.get(userId) : null;
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
