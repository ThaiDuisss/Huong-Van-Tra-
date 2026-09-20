package org.hvtra.iventoryservice.counting.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.logging.action.UserAction;
import org.hvtra.iventoryservice.counting.dto.request.CreateAdjustmentRequest;
import org.hvtra.iventoryservice.counting.dto.response.AdjustmentResponse;
import org.hvtra.iventoryservice.counting.service.InventoryAdjustmentService;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/adjustments")
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService service;

    public InventoryAdjustmentController(InventoryAdjustmentService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdjustmentResponse> findAll(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) InventoryDocumentStatus status,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return service.findAll(branchId, status, keyword, pageable);
    }

    @GetMapping("/{id}")
    public AdjustmentResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @UserAction(value = "INVENTORY_ADJUSTMENT_CREATE", entityType = "InventoryAdjustment", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentResponse create(@Valid @RequestBody CreateAdjustmentRequest request) {
        return service.create(request);
    }

    @UserAction(value = "INVENTORY_ADJUSTMENT_APPROVE", entityType = "InventoryAdjustment", entityId = "#id")
    @PostMapping("/{id}/approve")
    public AdjustmentResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @UserAction(value = "INVENTORY_ADJUSTMENT_POST", entityType = "InventoryAdjustment", entityId = "#id")
    @PostMapping("/{id}/post")
    public AdjustmentResponse post(@PathVariable UUID id) {
        return service.post(id);
    }

    @UserAction(value = "INVENTORY_ADJUSTMENT_REJECT", entityType = "InventoryAdjustment", entityId = "#id")
    @PostMapping("/{id}/reject")
    public AdjustmentResponse reject(@PathVariable UUID id) {
        return service.reject(id);
    }
}
