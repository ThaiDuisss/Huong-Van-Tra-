package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.request.SupplierRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.SupplierResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.SupplierService;
import org.springframework.data.domain.Pageable;
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
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.findAll(search, status, ingredientId, pageable);
    }

    @GetMapping("/{id}")
    public SupplierResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @PostMapping("/batch")
    public List<SupplierResponse> getSuppliersBatch(@RequestBody List<UUID> ids) {
        return service.findByIds(ids);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SupplierRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public SupplierResponse updateStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }
}
