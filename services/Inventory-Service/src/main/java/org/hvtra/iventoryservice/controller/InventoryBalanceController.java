package org.hvtra.iventoryservice.controller;

import org.hvtra.iventoryservice.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.service.InventoryBalanceService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/view")
    public PageResponse<org.hvtra.iventoryservice.dto.response.InventoryBalanceViewResponse> findAllView(
            @RequestParam(name = "branchId", required = false) UUID branchId,
            @RequestParam(name = "locationId", required = false) UUID locationId,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productCode", required = false) String productCode,
            @RequestParam(name = "productName", required = false) String productName,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "unitId", required = false) UUID unitId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.findAllView(branchId, locationId, sku, productCode, productName, categoryCode, categoryId, unitId, pageable);
    }
}
