package org.hvtra.iventoryservice.movement.mapper;

import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.movement.dto.response.InventoryTransactionResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Component
public class InventoryTransactionMapper {

    public InventoryTransactionResponse toResponse(InventoryTransaction transaction) {
        return toResponse(transaction, Map.of(), Map.of());
    }

    public InventoryTransactionResponse toResponse(
            InventoryTransaction transaction,
            Map<UUID, String> userNames,
            Map<UUID, String> branchNames) {
        if (transaction == null) {
            return null;
        }

        String createdByName = transaction.getCreatedBy() != null && userNames != null
                ? userNames.get(transaction.getCreatedBy())
                : null;
        String branchName = transaction.getBranchId() != null && branchNames != null
                ? branchNames.get(transaction.getBranchId())
                : null;

        return new InventoryTransactionResponse(
                transaction.getId(),
                transaction.getTransactionCode(),
                transaction.getType(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getBranchId(),
                branchName,
                transaction.getCreatedBy(),
                createdByName,
                transaction.getCreatedAt(),
                transaction.getLines() != null
                        ? transaction.getLines().stream().map(this::toLineResponse).toList()
                        : Collections.emptyList()
        );
    }

    public InventoryTransactionResponse.LineResponse toLineResponse(InventoryTransactionLine line) {
        if (line == null) {
            return null;
        }
        String itemCode = line.getInventoryItem() != null ? line.getInventoryItem().getReferenceCode() : null;
        String itemName = line.getInventoryItem() != null ? line.getInventoryItem().getName() : null;
        String locationCode = line.getLocation() != null ? line.getLocation().getCode() : null;
        String locationName = line.getLocation() != null ? line.getLocation().getName() : null;

        return new InventoryTransactionResponse.LineResponse(
                line.getId(),
                line.getInventoryItem() != null ? line.getInventoryItem().getId() : null,
                itemCode,
                itemName,
                line.getLocation() != null ? line.getLocation().getId() : null,
                locationCode,
                locationName,
                line.getQuantity(),
                line.getBeforeQuantity(),
                line.getAfterQuantity()
        );
    }
}
