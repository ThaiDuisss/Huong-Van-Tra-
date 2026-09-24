package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.request.SupplierRequest;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.SupplierResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.SupplierService;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SupplierResponse> findAll(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) Status status,
            @RequestParam(name = "ingredientId", required = false) UUID ingredientId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(search, status, ingredientId, pageable);
    }

    @GetMapping("/lookup")
    public PageResponse<LookupResponse> lookup(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "ids", required = false) List<UUID> ids,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.lookup(search, ids, pageable);
    }

    @GetMapping("/{id}")
    public SupplierResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @PostMapping("/batch")
    public List<SupplierResponse> getSuppliersBatch(@RequestBody List<UUID> ids) {
        return service.findByIds(ids);
    }

    @UserAction(value = "SUPPLIER_CREATE", entityType = "Supplier", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return service.create(request);
    }

    @UserAction(value = "SUPPLIER_UPDATE", entityType = "Supplier", entityId = "#id")
    @PutMapping("/{id}")
    public SupplierResponse update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SupplierRequest request) {
        return service.update(id, request);
    }

    @UserAction(value = "SUPPLIER_STATUS_UPDATE", entityType = "Supplier", entityId = "#id", detail = "#request.status")
    @PatchMapping("/{id}/status")
    public SupplierResponse updateStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @UserAction(value = "SUPPLIER_DELETE", entityType = "Supplier", entityId = "#id")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }
}
