package org.hvtra.iventoryservice.item.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.item.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.item.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.item.service.InventoryItemService;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.shared.web.InventoryStatusRequest;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.stock.service.InventoryBalanceService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/inventory-items")
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
            @RequestParam(name = "referenceId", required = false) UUID referenceId,
            @RequestParam(name = "referenceCode", required = false) String referenceCode,
            @RequestParam(name = "itemType", required = false) InventoryItemType itemType,
            @RequestParam(name = "status", required = false) InventoryStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(referenceId, referenceCode, itemType, status, pageable);
    }

    @GetMapping("/{id}")
    public InventoryItemResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/balances")
    public PageResponse<InventoryBalanceResponse> findBalances(
            @PathVariable("id") UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return balanceService.findByInventoryItem(id, pageable);
    }

    @UserAction(value = "INVENTORY_ITEM_CREATE", entityType = "InventoryItem", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemResponse create(@Valid @RequestBody InventoryItemRequest request) {
        return service.create(request);
    }

    @UserAction(value = "INVENTORY_ITEM_UPDATE", entityType = "InventoryItem", entityId = "#id")
    @PutMapping("/{id}")
    public InventoryItemResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody InventoryItemRequest request) {
        return service.update(id, request);
    }

    @UserAction(value = "INVENTORY_ITEM_UPDATE", entityType = "InventoryItem", entityId = "#result.id", detail = "#itemType + ':' + #referenceId")
    @PutMapping("/by-reference/{itemType}/{referenceId}")
    public InventoryItemResponse updateByReference(
            @PathVariable("itemType") InventoryItemType itemType,
            @PathVariable("referenceId") UUID referenceId,
            @Valid @RequestBody InventoryItemRequest request) {
        return service.updateByReference(referenceId, itemType, request);
    }

    @UserAction(value = "INVENTORY_ITEM_STATUS_UPDATE", entityType = "InventoryItem", entityId = "#id", detail = "#request.status")
    @PatchMapping("/{id}/status")
    public InventoryItemResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody InventoryStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
