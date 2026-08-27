package org.hvtra.iventoryservice.counting.controller;

import jakarta.validation.Valid;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentResponse create(@Valid @RequestBody CreateAdjustmentRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/approve")
    public AdjustmentResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/post")
    public AdjustmentResponse post(@PathVariable UUID id) {
        return service.post(id);
    }

    @PostMapping("/{id}/reject")
    public AdjustmentResponse reject(@PathVariable UUID id) {
        return service.reject(id);
    }
}
