package org.hvtra.iventoryservice.stock.mapper;

import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryBalanceMapper {

    public InventoryBalanceResponse toResponse(InventoryBalance balance) {
        return new InventoryBalanceResponse(
                balance.getId(),
                balance.getInventoryBatch() != null ? balance.getInventoryBatch().getId() : null,
                balance.getInventoryItem() != null ? balance.getInventoryItem().getId() : null,
                balance.getInventoryBatch() != null ? balance.getInventoryBatch().getBatchCode() : null,
                balance.getLocation().getId(),
                balance.getLocation().getBranchId(),
                balance.getQuantityOnHand(),
                balance.getQuantityReserved(),
                balance.getQuantityBlocked(),
                balance.getAvailableQuantity(),
                balance.getVersion(),
                balance.getUpdatedAt()
        );
    }
}
