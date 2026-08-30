package org.hvtra.iventoryservice.counting.mapper;

import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeLineResponse;
import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeResponse;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.springframework.stereotype.Component;
import org.hvtra.commonservice.utils.SecurityUtils;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;

import java.util.List;

@Component
public class InventoryTakeMapper {

    public InventoryTakeResponse toResponse(InventoryCount count) {
        var adjustment = count.getAdjustmentDocument();
        return new InventoryTakeResponse(
                count.getId(),
                count.getCountCode(),
                count.getBranchId(),
                count.getWarehouseId(),
                count.getCountType(),
                count.getScopeType(),
                count.getStatus(),
                count.getLockMode(),
                count.getBlindCount(),
                count.getSnapshotAt(),
                count.getCountingStartedAt(),
                count.getCountingCompletedAt(),
                count.getNote(),
                count.getCreatedAt(),
                count.getCreatedBy(),
                List.copyOf(count.getScopeLocationIds()),
                List.copyOf(count.getScopeInventoryItemIds()),
                List.copyOf(count.getScopeBatchIds()),
                count.getAssignments().stream()
                        .map(assignment -> new InventoryTakeResponse.AssignmentSummary(
                                assignment.getId(),
                                assignment.getUserId(),
                                assignment.getRole(),
                                assignment.getStatus(),
                                assignment.getLocation() == null ? null : assignment.getLocation().getId()))
                        .toList(),
                adjustment == null ? null : new InventoryTakeResponse.AdjustmentSummary(
                        adjustment.getId(),
                        adjustment.getDocumentCode(),
                        adjustment.getDocumentType(),
                        adjustment.getStatus(),
                        adjustment.getApprovedBy(),
                        adjustment.getApprovedAt(),
                        adjustment.getPostedAt()
                ),
                count.getLines().stream()
                        .map(line -> toLineResponse(line, shouldHideExpectedQuantities(count)))
                        .toList()
        );
    }

    private InventoryTakeLineResponse toLineResponse(InventoryCountLine line, boolean hideExpected) {
        InventoryBatch batch = line.getInventoryBatch();
        return new InventoryTakeLineResponse(
                line.getId(),
                line.getInventoryItem().getId(),
                line.getLocation().getId(),
                batch != null ? batch.getId() : null,
                batch != null ? batch.getBatchCode() : null,
                batch != null ? batch.getExpiryDate() : null,
                hideExpected ? null : line.getSnapshotQuantity(),
                line.getMovementQuantity(),
                hideExpected ? null : line.getExpectedQuantity(),
                hideExpected ? null : line.getSnapshotUnitCost(),
                line.getCountedQuantity(),
                line.getVarianceQuantity(),
                line.getVarianceValue(),
                line.getGoodQuantity(),
                line.getDamagedQuantity(),
                line.getExpiredQuantity(),
                line.getQuarantinedQuantity(),
                line.getReasonCode(),
                line.getNote(),
                line.getAttempts().stream()
                        .map(attempt -> new InventoryTakeLineResponse.AttemptSummary(
                                attempt.getId(), attempt.getAttemptNo(), attempt.getCounterId(),
                                attempt.getCountedQuantity(), attempt.getStatus(), attempt.getNote(),
                                attempt.getSubmittedAt()))
                        .toList()
        );
    }

    private boolean shouldHideExpectedQuantities(InventoryCount count) {
        if (!Boolean.TRUE.equals(count.getBlindCount()) || count.getStatus() != InventoryCountStatus.COUNTING) {
            return false;
        }
        return !(SecurityUtils.hasRole("ADMIN")
                || SecurityUtils.hasRole("BRANCH_MANAGER")
                || SecurityUtils.hasRole("INVENTORY_MANAGER"));
    }
}
