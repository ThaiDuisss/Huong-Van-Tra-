package org.hvtra.iventoryservice.mapper;

import org.hvtra.iventoryservice.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.entity.InventoryItem;
import org.springframework.stereotype.Component;

@Component
public class InventoryItemMapper {
    public InventoryItemResponse toResponse(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getReferenceId(),
                item.getItemType(),
                item.getBaseUnitId(),
                item.getStatus()
        );
    }

    public void update(InventoryItem item, InventoryItemRequest request) {
        item.setReferenceId(request.referenceId());
        item.setItemType(request.itemType());
        item.setBaseUnitId(request.baseUnitId());
        item.setStatus(request.status());
    }
}
