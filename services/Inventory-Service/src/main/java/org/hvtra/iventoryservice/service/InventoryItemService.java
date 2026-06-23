package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryItemService {
    PageResponse<InventoryItemResponse> findAll(
            UUID referenceId, InventoryItemType itemType, InventoryStatus status, Pageable pageable);
    InventoryItemResponse findById(UUID id);
    InventoryItemResponse create(InventoryItemRequest request);
    InventoryItemResponse update(UUID id, InventoryItemRequest request);
    InventoryItemResponse updateStatus(UUID id, InventoryStatus status);
}
