package org.hvtra.iventoryservice.mapper;

import org.hvtra.iventoryservice.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.springframework.stereotype.Component;

@Component
public class InventoryBalanceMapper {
    public InventoryBalanceResponse toResponse(InventoryBalance balance) {
        return new InventoryBalanceResponse(
                balance.getId(),
                balance.getInventoryItem().getId(),
                balance.getLocation().getId(),
                balance.getLocation().getBranchId(),
                balance.getQuantityOnHand(),
                balance.getQuantityReserved(),
                balance.getQuantityOnHand().subtract(balance.getQuantityReserved()),
                balance.getVersion(),
                balance.getUpdatedAt()
        );
    }
}
