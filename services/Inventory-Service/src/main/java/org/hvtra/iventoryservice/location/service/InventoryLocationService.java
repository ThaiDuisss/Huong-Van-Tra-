package org.hvtra.iventoryservice.location.service;

import org.hvtra.iventoryservice.location.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationHierarchyResponse;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.location.dto.response.LookupResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InventoryLocationService {
    PageResponse<InventoryLocationResponse> findAll(
            UUID branchId, String query, InventoryLocationType type, InventoryStatus status, Pageable pageable);
    List<InventoryLocationHierarchyResponse> getAll(
            UUID branchId, String query, InventoryLocationType type, InventoryStatus status);
    InventoryLocationResponse findById(UUID id);
    InventoryLocationResponse create(InventoryLocationRequest request);
    InventoryLocationResponse update(UUID id, InventoryLocationRequest request);
    InventoryLocationResponse updateStatus(UUID id, InventoryStatus status);
    InventoryLocationResponse findDefaultWarehouse(UUID branchId);

    PageResponse<LookupResponse> lookup(
            String search, InventoryLocationType type, InventoryStatus status, List<UUID> ids, Pageable pageable);
}
