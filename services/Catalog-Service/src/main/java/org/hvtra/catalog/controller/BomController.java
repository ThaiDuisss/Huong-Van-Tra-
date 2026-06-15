package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.BomRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.BomResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.BomService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/boms")
public class BomController {
    private final BomService service;

    public BomController(BomService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<BomResponse> findAll(
            @RequestParam(name = "productVariantId", required = false) Long productVariantId,
            @RequestParam(name = "version", required = false) Integer version,
            @RequestParam(name = "status", required = false) Status status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.findAll(productVariantId, version, status, pageable);
    }

    @GetMapping("/{id}")
    public BomResponse findById(@PathVariable("id") Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BomResponse create(@Valid @RequestBody BomRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public BomResponse update(@PathVariable("id") Long id, @Valid @RequestBody BomRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public BomResponse updateStatus(
            @PathVariable("id") Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
