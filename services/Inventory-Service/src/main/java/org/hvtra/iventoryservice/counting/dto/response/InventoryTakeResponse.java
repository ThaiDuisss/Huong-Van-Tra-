package org.hvtra.iventoryservice.counting.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountLockMode;
import org.hvtra.iventoryservice.domain.enums.InventoryCountType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAssignmentRole;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAssignmentStatus;

public record InventoryTakeResponse(
        UUID id,
        String countCode,
        UUID branchId,
        UUID warehouseId,
        InventoryCountType countType,
        InventoryCountScopeType scopeType,
        InventoryCountStatus status,
        InventoryCountLockMode lockMode,
        Boolean blindCount,
        Instant startedAt,
        Instant submittedAt,
        Instant completedAt,
        UUID completedBy,
        String returnReason,
        Instant cancelledAt,
        UUID cancelledBy,
        String cancelReason,
        String note,
        Instant createdAt,
        UUID createdBy,
        List<UUID> locationIds,
        List<UUID> inventoryItemIds,
        List<UUID> batchIds,
        List<AssignmentSummary> assignments,
        AdjustmentSummary adjustmentDocument,
        List<InventoryTakeLineResponse> lines
) {
    public record AssignmentSummary(
            UUID id,
            UUID userId,
            InventoryCountAssignmentRole role,
            InventoryCountAssignmentStatus status,
            UUID locationId
    ) {}

    public record AdjustmentSummary(
            UUID id,
            String documentCode,
            InventoryDocumentType documentType,
            InventoryDocumentStatus status,
            UUID approvedBy,
            Instant approvedAt,
            Instant postedAt
    ) {}
}
