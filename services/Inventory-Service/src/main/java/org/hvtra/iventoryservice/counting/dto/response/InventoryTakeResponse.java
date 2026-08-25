package org.hvtra.iventoryservice.counting.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryTakeResponse(
        UUID id,
        String countCode,
        UUID branchId,
        InventoryCountScopeType scopeType,
        InventoryCountStatus status,
        Instant snapshotAt,
        Instant countingStartedAt,
        Instant countingCompletedAt,
        String note,
        Instant createdAt,
        UUID createdBy,
        List<InventoryTakeLineResponse> lines
) {}
