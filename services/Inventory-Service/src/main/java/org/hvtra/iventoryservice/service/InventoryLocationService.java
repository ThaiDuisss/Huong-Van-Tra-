package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryLocationType;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import org.springframework.data.domain.Pageable;

public interface InventoryLocationService {
    PageResponse<InventoryLocationResponse> findAll(
            Long branchId, InventoryLocationType type, InventoryStatus status, Pageable pageable);
    InventoryLocationResponse findById(Long id);
    InventoryLocationResponse create(InventoryLocationRequest request);
    InventoryLocationResponse update(Long id, InventoryLocationRequest request);
    InventoryLocationResponse updateStatus(Long id, InventoryStatus status);
}
