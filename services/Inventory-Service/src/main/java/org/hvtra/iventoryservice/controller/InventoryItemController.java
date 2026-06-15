package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.dto.request.InventoryStatusRequest;
import org.hvtra.iventoryservice.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import org.hvtra.iventoryservice.service.InventoryBalanceService;
import org.hvtra.iventoryservice.service.InventoryItemService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory-items")
public class InventoryItemController {
    private final InventoryItemService service;
    private final InventoryBalanceService balanceService;

    public InventoryItemController(
            InventoryItemService service, InventoryBalanceService balanceService) {
        this.service = service;
        this.balanceService = balanceService;
    }

    @GetMapping
    public PageResponse<InventoryItemResponse> findAll(
            @RequestParam(required = false) Long referenceId,
            @RequestParam(required = false) InventoryItemType itemType,
            @RequestParam(required = false) InventoryStatus status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.findAll(referenceId, itemType, status, pageable);
    }

    @GetMapping("/{id}")
    public InventoryItemResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/balances")
    public PageResponse<InventoryBalanceResponse> findBalances(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return balanceService.findByInventoryItem(id, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemResponse create(@Valid @RequestBody InventoryItemRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public InventoryItemResponse update(
            @PathVariable Long id, @Valid @RequestBody InventoryItemRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public InventoryItemResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody InventoryStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
