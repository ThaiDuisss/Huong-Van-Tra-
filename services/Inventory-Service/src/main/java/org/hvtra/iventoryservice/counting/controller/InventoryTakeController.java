package org.hvtra.iventoryservice.counting.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.logging.action.UserAction;
import org.hvtra.iventoryservice.counting.dto.request.CreateInventoryTakeRequest;
import org.hvtra.iventoryservice.counting.dto.request.UpdateInventoryCountsRequest;
import org.hvtra.iventoryservice.counting.dto.request.InventoryCountDecisionRequest;
import org.hvtra.iventoryservice.counting.dto.request.RequestInventoryRecountRequest;
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

    @UserAction(value = "INVENTORY_TAKE_FREEZE", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/freeze")
    public InventoryTakeResponse freeze(@PathVariable UUID id) {
        return service.freeze(id);
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

    @UserAction(value = "INVENTORY_TAKE_SUBMIT_APPROVAL", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/submit-approval")
    public InventoryTakeResponse submitApproval(@PathVariable UUID id) {
        return service.submitApproval(id);
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
            @RequestBody InventoryCountDecisionRequest request) {
        return service.returnForRecount(id, request.reason());
    }

    @UserAction(value = "INVENTORY_TAKE_REOPEN", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/reopen")
    public InventoryTakeResponse reopen(@PathVariable UUID id) {
        return service.reopen(id);
    }

    @UserAction(value = "INVENTORY_TAKE_REJECT", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/reject")
    public InventoryTakeResponse reject(
            @PathVariable UUID id,
            @RequestBody InventoryCountDecisionRequest request) {
        return service.reject(id, request.reason());
    }

    @UserAction(value = "INVENTORY_TAKE_CANCEL", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/cancel")
    public InventoryTakeResponse cancel(
            @PathVariable UUID id,
            @RequestBody InventoryCountDecisionRequest request) {
        return service.cancel(id, request.reason());
    }

    @UserAction(value = "INVENTORY_TAKE_CLOSE", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/close")
    public InventoryTakeResponse close(@PathVariable UUID id) {
        return service.close(id);
    }

    @UserAction(value = "INVENTORY_TAKE_RECOUNT_REQUEST", entityType = "InventoryTake", entityId = "#id")
    @PostMapping("/{id}/recounts")
    public InventoryTakeResponse requestRecount(
            @PathVariable UUID id,
            @Valid @RequestBody RequestInventoryRecountRequest request) {
        return service.requestRecount(id, request);
    }
}
