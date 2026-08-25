package org.hvtra.iventoryservice.counting.mapper;

import org.hvtra.iventoryservice.counting.dto.response.AdjustmentLineResponse;
import org.hvtra.iventoryservice.counting.dto.response.AdjustmentResponse;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.springframework.stereotype.Component;

@Component
public class AdjustmentMapper {

    public AdjustmentResponse toResponse(InventoryDocument document) {
        return new AdjustmentResponse(
                document.getId(),
                document.getDocumentCode(),
                document.getDocumentType(),
                document.getStatus(),
                document.getBranchId(),
                document.getReasonCode(),
                document.getNote(),
                document.getApprovedBy(),
                document.getApprovedAt(),
                document.getPostedAt(),
                document.getCreatedAt(),
                document.getCreatedBy(),
                document.getLines().stream().map(this::toLineResponse).toList()
        );
    }

    public AdjustmentLineResponse toLineResponse(InventoryDocumentLine line) {
        return new AdjustmentLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getInventoryItem().getId(),
                line.getSourceLocation() != null ? line.getSourceLocation().getId() : null,
                line.getRequestedQuantity(),
                line.getProcessedQuantity(),
                line.getNote()
        );
    }
}
