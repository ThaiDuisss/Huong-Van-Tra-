package org.hvtra.iventoryservice.counting.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.counting.dto.request.CreateInventoryTakeRequest;
import org.hvtra.iventoryservice.counting.dto.request.UpdateInventoryCountsRequest;
import org.hvtra.iventoryservice.counting.dto.request.InventoryCountDecisionRequest;
import org.hvtra.iventoryservice.counting.dto.request.ReturnForRecountRequest;
import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeResponse;
import org.hvtra.iventoryservice.counting.service.InventoryTakeService;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/inventorytakes")
public class InventoryTakeController {

    private final InventoryTakeService service;

    public InventoryTakeController(InventoryTakeService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InventoryTakeResponse> findAll(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(name = "statuses", required = false) List<InventoryCountStatus> statuses,
            @RequestParam(required = false) InventoryCountScopeType scopeType,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return service.findAll(branchId, statuses, scopeType, keyword, pageable);
    }

    @GetMapping("/{id}")
    public InventoryTakeResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @UserAction(value = "INVENTORY_TAKE_CREATE", entityType = "InventoryTake", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTakeResponse create(@Valid @RequestBody CreateInventoryTakeRequest request) {
        return service.create(request);
    }

    @UserAction(value = "INVENTORY_TAKE_START", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/start")
    public InventoryTakeResponse start(@PathVariable UUID id) {
        return service.start(id);
    }

    @UserAction(value = "INVENTORY_TAKE_COUNTS_UPDATE", entityType = "InventoryTake", entityId = "#id")
    @PutMapping("/{id}/counts")
    public InventoryTakeResponse updateCounts(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInventoryCountsRequest request) {
        return service.updateCounts(id, request);
    }

    @UserAction(value = "INVENTORY_TAKE_FINISH", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/finish")
    public InventoryTakeResponse finish(@PathVariable UUID id) {
        return service.finish(id);
    }

    @UserAction(value = "INVENTORY_TAKE_APPROVE", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/approve")
    public InventoryTakeResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @UserAction(value = "INVENTORY_TAKE_RETURN", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/return")
    public InventoryTakeResponse returnForRecount(
            @PathVariable UUID id,
            @Valid @RequestBody ReturnForRecountRequest request) {
        return service.returnForRecount(id, request);
    }

    @UserAction(value = "INVENTORY_TAKE_CANCEL", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/cancel")
    public InventoryTakeResponse cancel(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryCountDecisionRequest request) {
        return service.cancel(id, request.reason());
    }
}
