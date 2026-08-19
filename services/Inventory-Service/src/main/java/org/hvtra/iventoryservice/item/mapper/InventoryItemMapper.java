package org.hvtra.iventoryservice.item.mapper;

import org.hvtra.iventoryservice.item.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.item.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.springframework.stereotype.Component;

@Component
public class InventoryItemMapper {
    public InventoryItemResponse toResponse(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getReferenceId(),
                item.getReferenceCode(),
                item.getItemType(),
                item.getUnitId(),
                item.getStatus(),
                item.getReorderPoint()
        );
    }

    public void update(InventoryItem item, InventoryItemRequest request) {
        item.setReferenceId(request.referenceId());
        item.setReferenceCode(request.referenceCode());
        item.setItemType(request.itemType());
        item.setUnitId(request.unitId());
        item.setStatus(request.status());
        item.setReorderPoint(request.reorderPoint());
    }
}
