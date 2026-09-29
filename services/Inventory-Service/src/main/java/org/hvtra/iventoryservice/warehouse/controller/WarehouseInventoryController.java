package org.hvtra.iventoryservice.warehouse.controller;

import lombok.RequiredArgsConstructor;
import org.hvtra.iventoryservice.warehouse.dto.LocationInventoryDetailResponse;
import org.hvtra.iventoryservice.warehouse.dto.WarehouseInventorySummaryResponse;
import org.hvtra.iventoryservice.warehouse.service.WarehouseInventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/warehouse")
@RequiredArgsConstructor
public class WarehouseInventoryController {

    private final WarehouseInventoryService warehouseInventoryService;

    /**
     * Lấy danh sách sản phẩm với tồn kho (tách SHELF vs WAREHOUSE)
     * GET /api/inventory/warehouse/products?branchId=...&keyword=...&page=0&size=20
     */
    @GetMapping("/products")
    public ResponseEntity<Page<WarehouseInventorySummaryResponse>> getProductInventorySummary(
        @RequestParam UUID branchId,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        var result = warehouseInventoryService.getProductInventorySummary(branchId, keyword, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Lấy chi tiết tồn kho của sản phẩm theo từng location
     * GET /api/inventory/warehouse/products/{itemId}/locations
     */
    @GetMapping("/products/{itemId}/locations")
    public ResponseEntity<List<LocationInventoryDetailResponse>> getProductLocationInventory(
        @PathVariable UUID itemId
    ) {
        var result = warehouseInventoryService.getProductLocationInventory(itemId);
        return ResponseEntity.ok(result);
    }
}
