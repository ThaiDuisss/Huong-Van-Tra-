package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.dto.request.InventoryStatusRequest;
import org.hvtra.iventoryservice.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryLocationType;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import org.hvtra.iventoryservice.service.InventoryLocationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory-locations")
public class InventoryLocationController {
    private final InventoryLocationService service;

    public InventoryLocationController(InventoryLocationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InventoryLocationResponse> findAll(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) InventoryLocationType type,
            @RequestParam(required = false) InventoryStatus status,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return service.findAll(branchId, type, status, pageable);
    }

    @GetMapping("/{id}")
    public InventoryLocationResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryLocationResponse create(
            @Valid @RequestBody InventoryLocationRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public InventoryLocationResponse update(
            @PathVariable Long id, @Valid @RequestBody InventoryLocationRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public InventoryLocationResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody InventoryStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
