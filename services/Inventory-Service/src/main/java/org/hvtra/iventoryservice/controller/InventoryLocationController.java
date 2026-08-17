package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.dto.request.InventoryStatusRequest;
import org.hvtra.iventoryservice.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.shared.enums.InventoryLocationType;
import org.hvtra.iventoryservice.shared.enums.InventoryStatus;
import org.hvtra.iventoryservice.service.InventoryLocationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/inventory-locations")
public class InventoryLocationController {
    private final InventoryLocationService service;

    public InventoryLocationController(InventoryLocationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InventoryLocationResponse> findAll(
            @RequestParam(name = "branchId", required = false) UUID branchId,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "type", required = false) InventoryLocationType type,
            @RequestParam(name = "status", required = false) InventoryStatus status,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return service.findAll(branchId, query, type, status, pageable);
    }

    @GetMapping("/{id}")
    public InventoryLocationResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @GetMapping("/default-warehouse")
    public InventoryLocationResponse findDefaultWarehouse(
            @RequestParam(name = "branchId") UUID branchId) {
        return service.findDefaultWarehouse(branchId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryLocationResponse create(
            @Valid @RequestBody InventoryLocationRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public InventoryLocationResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody InventoryLocationRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public InventoryLocationResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody InventoryStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
