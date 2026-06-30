package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.InventoryStatusRequest;
import org.hvtra.iventoryservice.dto.request.StoreShelfRequest;
import org.hvtra.iventoryservice.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import org.hvtra.iventoryservice.service.StoreShelfService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller cho màn hình Quản lý kệ (Store Shelf).
 *
 * Base path: /api/inventory/inventory-locations/store
 *
 * GET    /api/inventory/inventory-locations/store         – Danh sách kệ
 * GET    /api/inventory/inventory-locations/store/{id}    – Chi tiết kệ
 * POST   /api/inventory/inventory-locations/store         – Thêm kệ
 * PUT    /api/inventory/inventory-locations/store/{id}    – Sửa kệ
 * PATCH  /api/inventory/inventory-locations/store/{id}/status – Đổi trạng thái
 */
@RestController
@RequestMapping("/api/inventory/inventory-locations/store")
public class StoreShelfController {

    private final StoreShelfService service;

    public StoreShelfController(StoreShelfService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InventoryLocationResponse> findAll(
            @RequestParam(name = "status", required = false) InventoryStatus status,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return service.findAll(status, pageable);
    }

    @GetMapping("/{id}")
    public InventoryLocationResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryLocationResponse create(
            @Valid @RequestBody StoreShelfRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public InventoryLocationResponse update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody StoreShelfRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public InventoryLocationResponse updateStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody InventoryStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
