package org.hvtra.iventoryservice.counting.controller;

import jakarta.validation.Valid;
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
            @RequestParam(required = false) InventoryCountStatus status,
            @RequestParam(required = false) InventoryCountScopeType scopeType,
            Pageable pageable) {
        return service.findAll(branchId, status, scopeType, pageable);
    }

    @GetMapping("/{id}")
    public InventoryTakeResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTakeResponse create(@Valid @RequestBody CreateInventoryTakeRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/start")
    public InventoryTakeResponse start(@PathVariable UUID id) {
        return service.start(id);
    }

    @PostMapping("/{id}/freeze")
    public InventoryTakeResponse freeze(@PathVariable UUID id) {
        return service.freeze(id);
    }

    @PutMapping("/{id}/counts")
    public InventoryTakeResponse updateCounts(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInventoryCountsRequest request) {
        return service.updateCounts(id, request);
    }

    @PostMapping("/{id}/finish")
    public InventoryTakeResponse finish(@PathVariable UUID id) {
        return service.finish(id);
    }

    @PostMapping("/{id}/submit-approval")
    public InventoryTakeResponse submitApproval(@PathVariable UUID id) {
        return service.submitApproval(id);
    }

    @PostMapping("/{id}/approve")
    public InventoryTakeResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/return")
    public InventoryTakeResponse returnForRecount(
            @PathVariable UUID id,
            @RequestBody InventoryCountDecisionRequest request) {
        return service.returnForRecount(id, request.reason());
    }

    @PostMapping("/{id}/reopen")
    public InventoryTakeResponse reopen(@PathVariable UUID id) {
        return service.reopen(id);
    }

    @PostMapping("/{id}/reject")
    public InventoryTakeResponse reject(
            @PathVariable UUID id,
            @RequestBody InventoryCountDecisionRequest request) {
        return service.reject(id, request.reason());
    }

    @PostMapping("/{id}/cancel")
    public InventoryTakeResponse cancel(
            @PathVariable UUID id,
            @RequestBody InventoryCountDecisionRequest request) {
        return service.cancel(id, request.reason());
    }

    @PostMapping("/{id}/close")
    public InventoryTakeResponse close(@PathVariable UUID id) {
        return service.close(id);
    }

    @PostMapping("/{id}/recounts")
    public InventoryTakeResponse requestRecount(
            @PathVariable UUID id,
            @Valid @RequestBody RequestInventoryRecountRequest request) {
        return service.requestRecount(id, request);
    }
}
