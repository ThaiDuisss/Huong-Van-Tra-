package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryBalanceService {
    PageResponse<InventoryBalanceResponse> findAll(
            UUID inventoryItemId, UUID locationId, UUID branchId, Pageable pageable);
    InventoryBalanceResponse findById(UUID id);
    PageResponse<InventoryBalanceResponse> findByInventoryItem(UUID itemId, Pageable pageable);
    PageResponse<org.hvtra.iventoryservice.dto.response.InventoryBalanceViewResponse> findAllView(
            UUID branchId,
            UUID locationId,
            String sku,
            String productCode,
            String productName,
            String categoryCode,
            UUID categoryId,
            UUID unitId,
            Pageable pageable
    );
}
