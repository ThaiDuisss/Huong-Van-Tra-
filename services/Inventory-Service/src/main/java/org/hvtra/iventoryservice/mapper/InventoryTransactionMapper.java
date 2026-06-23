package org.hvtra.iventoryservice.mapper;

import org.hvtra.iventoryservice.dto.response.InventoryTransactionResponse;
import org.hvtra.iventoryservice.entity.InventoryTransaction;
import org.springframework.stereotype.Component;

@Component
public class InventoryTransactionMapper {
    public InventoryTransactionResponse toResponse(InventoryTransaction transaction) {
        return new InventoryTransactionResponse(
                transaction.getId(),
                transaction.getTransactionCode(),
                transaction.getType(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getBranchId(),
                transaction.getCreatedBy(),
                transaction.getCreatDate(),
                transaction.getLines().stream().map(line -> new InventoryTransactionResponse.LineResponse(
                        line.getId(),
                        line.getInventoryItem().getId(),
                        line.getLocation().getId(),
                        line.getQuantity(),
                        line.getBeforeQuantity(),
                        line.getAfterQuantity()
                )).toList()
        );
    }
}
