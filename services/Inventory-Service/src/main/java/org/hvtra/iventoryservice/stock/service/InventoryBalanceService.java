package org.hvtra.iventoryservice.stock.service;

import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceViewResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceWarehouseResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.domain.enums.StockType;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.List;

public interface InventoryBalanceService {
    PageResponse<InventoryBalanceResponse> findAll(
            UUID inventoryItemId, UUID locationId, UUID branchId, Pageable pageable);
    InventoryBalanceResponse findById(UUID id);
    PageResponse<InventoryBalanceResponse> findByInventoryItem(UUID itemId, Pageable pageable);
    PageResponse<InventoryBalanceViewResponse> findAllView(
            String keyword,
            String code,
            String sku,
            String productCode,
            String name,
            String productName,
            String categoryCode,
            UUID categoryId,
            UUID unitId,
            UUID locationId,
            UUID branchId,
            StockType status,
            org.hvtra.iventoryservice.domain.enums.InventoryItemType itemType,
            Pageable pageable
    );
    List<InventoryBalanceWarehouseResponse> findWarehouseBalances(
            String sku,
            String productName
    );
    void bulkUpdateLocation();
}
