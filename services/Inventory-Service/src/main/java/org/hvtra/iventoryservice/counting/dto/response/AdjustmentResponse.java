package org.hvtra.iventoryservice.counting.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdjustmentResponse(
        UUID id,
        String documentCode,
        InventoryDocumentType documentType,
        InventoryDocumentStatus status,
        UUID branchId,
        String reasonCode,
        String note,
        UUID approvedBy,
        Instant approvedAt,
        Instant postedAt,
        Instant createdAt,
        UUID createdBy,
        List<AdjustmentLineResponse> lines
) {}
