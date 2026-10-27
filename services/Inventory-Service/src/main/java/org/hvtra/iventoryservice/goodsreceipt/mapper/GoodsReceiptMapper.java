package org.hvtra.iventoryservice.goodsreceipt.mapper;

import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptBatchAllocationResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptLineResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptResponse;
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
public class GoodsReceiptMapper {

    public GoodsReceiptResponse toResponse(InventoryDocument document) {
        return toResponse(document, null);
    }

    public GoodsReceiptResponse toResponse(InventoryDocument document, String supplierName) {
        return toResponse(document, supplierName, Map.of());
    }

    /** @param userNames tên người dùng theo Keycloak subject (createdBy, approvedBy...), thiếu thì để null */
    public GoodsReceiptResponse toResponse(InventoryDocument document, String supplierName, Map<UUID, String> userNames) {
        if (document == null) {
            return null;
        }

        List<GoodsReceiptLineResponse> lines = document.getLines() != null
                ? document.getLines().stream().map(this::toLineResponse).toList()
                : Collections.emptyList();

        BigDecimal totalOrdered = lines.stream()
                .map(GoodsReceiptLineResponse::orderedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReceived = lines.stream()
                .map(GoodsReceiptLineResponse::receivedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAccepted = lines.stream()
                .map(GoodsReceiptLineResponse::acceptedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRejected = lines.stream()
                .map(GoodsReceiptLineResponse::rejectedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String locationName = document.getLocation() != null ? document.getLocation().getName() : null;
        String locationCode = document.getLocation() != null ? document.getLocation().getCode() : null;

        return new GoodsReceiptResponse(
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
                totalRejected,
                document.getExceptionApprovedBy(),
                nameOf(userNames, document.getExceptionApprovedBy()),
                document.getExceptionApprovedAt(),
                document.getExceptionApprovalNote()
        );
    }

    private static String nameOf(Map<UUID, String> userNames, UUID userId) {
        return userId != null ? userNames.get(userId) : null;
    }

    public GoodsReceiptLineResponse toLineResponse(InventoryDocumentLine line) {
        if (line == null) {
            return null;
        }

        List<GoodsReceiptBatchAllocationResponse> allocations = line.getBatchAllocations() != null
                ? line.getBatchAllocations().stream().map(this::toAllocationResponse).toList()
                : Collections.emptyList();

        String refCode = line.getInventoryItem() != null ? line.getInventoryItem().getReferenceCode() : null;
        String itemName = line.getInventoryItem() != null && line.getInventoryItem().getName() != null
                ? line.getInventoryItem().getName()
                : refCode;

        return new GoodsReceiptLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getInventoryItem() != null ? line.getInventoryItem().getId() : null,
                refCode,
                itemName,
                line.getInventoryItem() != null ? line.getInventoryItem().getItemType() : null,
                line.getUnitId(),
                null,
                line.getOrderedQuantity(),
                line.getReceivedQuantity(),
                line.getShortageQuantity(),
                line.getExcessQuantity(),
                line.getAcceptedQuantity(),
                line.getRejectedQuantity(),
                line.getNote(),
                allocations,
                line.getExcessResolution(),
                line.getShortageResolution(),
                line.getReturnedQuantity(),
                line.getPostedQuantity(),
                line.getResolutionNote()
        );
    }

    public GoodsReceiptBatchAllocationResponse toAllocationResponse(InventoryDocumentBatchAllocation allocation) {
        if (allocation == null) {
            return null;
        }

        var batch = allocation.getInventoryBatch();
        return new GoodsReceiptBatchAllocationResponse(
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
