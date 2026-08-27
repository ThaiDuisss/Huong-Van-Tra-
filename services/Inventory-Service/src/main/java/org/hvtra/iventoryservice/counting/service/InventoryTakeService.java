package org.hvtra.iventoryservice.counting.service;

import org.hvtra.iventoryservice.counting.dto.request.CreateInventoryTakeRequest;
import org.hvtra.iventoryservice.counting.dto.request.UpdateInventoryCountsRequest;
import org.hvtra.iventoryservice.counting.dto.request.RequestInventoryRecountRequest;
import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryTakeService {
    PageResponse<InventoryTakeResponse> findAll(UUID branchId, InventoryCountStatus status,
                                                 InventoryCountScopeType scopeType, Pageable pageable);
    InventoryTakeResponse findById(UUID id);
    InventoryTakeResponse create(CreateInventoryTakeRequest request);
    InventoryTakeResponse freeze(UUID id);
    InventoryTakeResponse start(UUID id);
    InventoryTakeResponse updateCounts(UUID id, UpdateInventoryCountsRequest request);
    InventoryTakeResponse finish(UUID id);
    InventoryTakeResponse submitApproval(UUID id);
    InventoryTakeResponse approve(UUID id);
    InventoryTakeResponse returnForRecount(UUID id, String reason);
    InventoryTakeResponse reopen(UUID id);
    InventoryTakeResponse reject(UUID id, String reason);
    InventoryTakeResponse cancel(UUID id, String reason);
    InventoryTakeResponse close(UUID id);
    InventoryTakeResponse requestRecount(UUID id, RequestInventoryRecountRequest request);
}
