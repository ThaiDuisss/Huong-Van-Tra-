package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface InventoryBalanceService {
    PageResponse<InventoryBalanceResponse> findAll(
            Long inventoryItemId, Long locationId, Long branchId, Pageable pageable);
    InventoryBalanceResponse findById(Long id);
    PageResponse<InventoryBalanceResponse> findByInventoryItem(Long itemId, Pageable pageable);
}
