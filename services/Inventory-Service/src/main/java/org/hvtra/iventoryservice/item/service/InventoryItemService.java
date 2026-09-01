package org.hvtra.iventoryservice.item.service;

import org.hvtra.iventoryservice.item.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.item.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

public interface InventoryItemService {
    PageResponse<InventoryItemResponse> findAll(
            UUID referenceId,
            String referenceCode,
            InventoryItemType itemType,
            InventoryStatus status,
            Pageable pageable);
    InventoryItemResponse findById(UUID id);
    InventoryItemResponse create(InventoryItemRequest request);
    InventoryItemResponse update(UUID id, InventoryItemRequest request);
    InventoryItemResponse updateByReference(UUID referenceId, InventoryItemType itemType, InventoryItemRequest request);
    InventoryItemResponse updateStatus(UUID id, InventoryStatus status);
}
