package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import org.springframework.data.domain.Pageable;

public interface InventoryItemService {
    PageResponse<InventoryItemResponse> findAll(
            Long referenceId, InventoryItemType itemType, InventoryStatus status, Pageable pageable);
    InventoryItemResponse findById(Long id);
    InventoryItemResponse create(InventoryItemRequest request);
    InventoryItemResponse update(Long id, InventoryItemRequest request);
    InventoryItemResponse updateStatus(Long id, InventoryStatus status);
}
