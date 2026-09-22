package org.hvtra.iventoryservice.stock.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceViewResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceWarehouseResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.service.InventoryBalanceService;
import org.hvtra.iventoryservice.domain.enums.StockType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(inventoryItemId, locationId, branchId, pageable);
    }

    @GetMapping("/{id}")
    public InventoryBalanceResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @GetMapping("/store")
    public PageResponse<InventoryBalanceViewResponse> findAllView(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productCode", required = false) String productCode,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "productName", required = false) String productName,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "unitId", required = false) UUID unitId,
            @RequestParam(name = "locationId", required = false) UUID locationId,
            @RequestParam(name = "branchId", required = false) UUID branchId,
            @RequestParam(name = "status", required = false) StockType status,
            @RequestParam(name = "type", required = false) org.hvtra.iventoryservice.domain.enums.InventoryItemType type,
            @RequestParam(name = "itemType", required = false) org.hvtra.iventoryservice.domain.enums.InventoryItemType itemType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        org.hvtra.iventoryservice.domain.enums.InventoryItemType resolvedType = itemType != null ? itemType : type;
        return service.findAllView(keyword, code, sku, productCode, name, productName, categoryCode, categoryId, unitId, locationId, branchId, status, resolvedType, pageable);
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

    @UserAction(value = "INVENTORY_BALANCE_LOCATION_BULK_UPDATE", entityType = "InventoryBalance")
    @PutMapping("/location")
    public void bulkUpdateLocation() {
        service.bulkUpdateLocation();
    }
}

