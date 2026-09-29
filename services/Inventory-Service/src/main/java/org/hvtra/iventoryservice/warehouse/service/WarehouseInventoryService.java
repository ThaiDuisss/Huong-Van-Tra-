package org.hvtra.iventoryservice.warehouse.service;

import org.hvtra.iventoryservice.warehouse.dto.LocationInventoryDetailResponse;
import org.hvtra.iventoryservice.warehouse.dto.WarehouseInventorySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WarehouseInventoryService {
    /**
     * Lấy danh sách sản phẩm với tồn kho (tách SHELF vs WAREHOUSE)
     */
    Page<WarehouseInventorySummaryResponse> getProductInventorySummary(
        UUID branchId,
        String keyword,
        Pageable pageable
    );

    /**
     * Lấy chi tiết tồn kho của sản phẩm theo từng location
     */
    List<LocationInventoryDetailResponse> getProductLocationInventory(UUID itemId);
}
