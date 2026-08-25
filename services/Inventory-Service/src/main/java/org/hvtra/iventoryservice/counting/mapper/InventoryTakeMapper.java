package org.hvtra.iventoryservice.counting.mapper;

import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeLineResponse;
import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeResponse;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.springframework.stereotype.Component;

@Component
public class InventoryTakeMapper {

    public InventoryTakeResponse toResponse(InventoryCount count) {
        return new InventoryTakeResponse(
                count.getId(),
                count.getCountCode(),
                count.getBranchId(),
                count.getScopeType(),
                count.getStatus(),
                count.getSnapshotAt(),
                count.getCountingStartedAt(),
                count.getCountingCompletedAt(),
                count.getNote(),
                count.getCreatedAt(),
                count.getCreatedBy(),
                count.getLines().stream().map(this::toLineResponse).toList()
        );
    }

    public InventoryTakeLineResponse toLineResponse(InventoryCountLine line) {
        InventoryBatch batch = line.getInventoryBatch();
        return new InventoryTakeLineResponse(
                line.getId(),
                line.getInventoryItem().getId(),
                line.getLocation().getId(),
                batch != null ? batch.getId() : null,
                batch != null ? batch.getBatchCode() : null,
                batch != null ? batch.getExpiryDate() : null,
                line.getSnapshotQuantity(),
                line.getCountedQuantity(),
                line.getVarianceQuantity(),
                line.getReasonCode(),
                line.getNote()
        );
    }
}
