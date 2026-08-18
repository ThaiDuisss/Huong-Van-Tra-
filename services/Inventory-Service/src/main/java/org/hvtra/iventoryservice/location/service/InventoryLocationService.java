package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryLocationService {
    PageResponse<InventoryLocationResponse> findAll(
            UUID branchId, String query, InventoryLocationType type, InventoryStatus status, Pageable pageable);
    InventoryLocationResponse findById(UUID id);
    InventoryLocationResponse create(InventoryLocationRequest request);
    InventoryLocationResponse update(UUID id, InventoryLocationRequest request);
    InventoryLocationResponse updateStatus(UUID id, InventoryStatus status);
    InventoryLocationResponse findDefaultWarehouse(UUID branchId);
}
