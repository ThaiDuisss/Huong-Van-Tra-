package org.hvtra.iventoryservice.controller;

import org.hvtra.iventoryservice.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.service.InventoryBalanceService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory-balances")
public class InventoryBalanceController {
    private final InventoryBalanceService service;

    public InventoryBalanceController(InventoryBalanceService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InventoryBalanceResponse> findAll(
            @RequestParam(required = false) Long inventoryItemId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long branchId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.findAll(inventoryItemId, locationId, branchId, pageable);
    }

    @GetMapping("/{id}")
    public InventoryBalanceResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
