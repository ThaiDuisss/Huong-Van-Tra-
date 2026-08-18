package org.hvtra.iventoryservice.stock.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceViewResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceWarehouseResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.service.InventoryBalanceService;
import org.hvtra.iventoryservice.domain.enums.StockType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/inventory/inventory-balances")
public class InventoryBalanceController {
    private final InventoryBalanceService service;

    public InventoryBalanceController(InventoryBalanceService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InventoryBalanceResponse> findAll(
            @RequestParam(name = "inventoryItemId", required = false) UUID inventoryItemId,
            @RequestParam(name = "locationId", required = false) UUID locationId,
            @RequestParam(name = "branchId", required = false) UUID branchId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.findAll(inventoryItemId, locationId, branchId, pageable);
    }

    @GetMapping("/{id}")
    public InventoryBalanceResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @GetMapping("/store")
    public PageResponse<InventoryBalanceViewResponse> findAllView(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productCode", required = false) String productCode,
            @RequestParam(name = "productName", required = false) String productName,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "unitId", required = false) UUID unitId,
            @RequestParam(name = "locationId", required = false) UUID locationId,
            @RequestParam(name = "branchId", required = false) UUID branchId,
            @RequestParam(name = "status", required = false) StockType status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.findAllView(keyword, sku, productCode, productName, categoryCode, categoryId, unitId, locationId, branchId, status, pageable);
    }

    @Tag(
            name = "API cho POS Inventory service",
            description = "Output: Id, locationId, inventoryItemId, variantId, itemType, quantityOnHand, quantityReserved, availableQuantity, status"
    )
    @GetMapping("/search-view-pos")
    public List<InventoryBalanceWarehouseResponse> findWarehouseBalances(
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productName", required = false) String productName) {
        return service.findWarehouseBalances(sku, productName);
    }

    @PutMapping("/location")
    public void bulkUpdateLocation() {
        service.bulkUpdateLocation();
    }
}

