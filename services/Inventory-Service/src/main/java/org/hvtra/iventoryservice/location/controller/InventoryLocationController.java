package org.hvtra.iventoryservice.location.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.location.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.shared.web.InventoryStatusRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.location.dto.response.LookupResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.location.service.InventoryLocationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(branchId, query, type, status, pageable);
    }

    /** Chỉ dùng cho dropdown: id + name, phân trang. */
    @GetMapping("/lookup")
    public PageResponse<LookupResponse> lookup(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "type", required = false) InventoryLocationType type,
            @RequestParam(name = "status", required = false) InventoryStatus status,
            @RequestParam(name = "ids", required = false) List<UUID> ids,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.lookup(search, type, status, ids, pageable);
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

    @UserAction(value = "INVENTORY_LOCATION_CREATE", entityType = "InventoryLocation", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryLocationResponse create(
            @Valid @RequestBody InventoryLocationRequest request) {
        return service.create(request);
    }

    @UserAction(value = "INVENTORY_LOCATION_UPDATE", entityType = "InventoryLocation", entityId = "#id")
    @PutMapping("/{id}")
    public InventoryLocationResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody InventoryLocationRequest request) {
        return service.update(id, request);
    }

    @UserAction(value = "INVENTORY_LOCATION_STATUS_UPDATE", entityType = "InventoryLocation", entityId = "#id", detail = "#request.status")
    @PatchMapping("/{id}/status")
    public InventoryLocationResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody InventoryStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
