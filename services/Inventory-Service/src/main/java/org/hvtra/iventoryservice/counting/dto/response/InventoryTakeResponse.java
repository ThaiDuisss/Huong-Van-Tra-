package org.hvtra.iventoryservice.counting.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
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
        Instant snapshotAt,
        Instant countingStartedAt,
        Instant countingCompletedAt,
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
            org.hvtra.iventoryservice.domain.enums.InventoryDocumentType documentType,
            org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus status,
            UUID approvedBy,
            Instant approvedAt,
            Instant postedAt
    ) {}
}
