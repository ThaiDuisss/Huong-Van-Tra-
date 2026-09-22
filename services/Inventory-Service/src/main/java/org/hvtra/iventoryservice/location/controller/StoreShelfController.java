package org.hvtra.iventoryservice.location.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.shared.web.InventoryStatusRequest;
import org.hvtra.iventoryservice.location.dto.request.StoreShelfRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.location.service.StoreShelfService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.hvtra.iventoryservice.location.dto.response.LookupResponse;

@RestController
@RequestMapping("/api/inventory/inventory-locations/store")
public class StoreShelfController {

    private final StoreShelfService service;

    public StoreShelfController(StoreShelfService service) {
        this.service = service;
    }

    @GetMapping("/lookup")
    public PageResponse<LookupResponse> lookup(
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.lookup(search, pageable);
    }

    @GetMapping
    public PageResponse<InventoryLocationResponse> findAll(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "status", required = false) InventoryStatus status,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return service.findAll(query, status, pageable);
    }

    @GetMapping("/{id}")
    public InventoryLocationResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @UserAction(value = "STORE_SHELF_CREATE", entityType = "StoreShelf", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryLocationResponse create(
            @Valid @RequestBody StoreShelfRequest request) {
        return service.create(request);
    }

    @UserAction(value = "STORE_SHELF_UPDATE", entityType = "StoreShelf", entityId = "#id")
    @PutMapping("/{id}")
    public InventoryLocationResponse update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody StoreShelfRequest request) {
        return service.update(id, request);
    }

    @UserAction(value = "STORE_SHELF_STATUS_UPDATE", entityType = "StoreShelf", entityId = "#id", detail = "#request.status")
    @PatchMapping("/{id}/status")
    public InventoryLocationResponse updateStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody InventoryStatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @UserAction(value = "STORE_SHELF_DELETE", entityType = "StoreShelf", entityId = "#id")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }
}
