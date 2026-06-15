package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.BomRequest;
import org.hvtra.iventoryservice.dto.request.StatusRequest;
import org.hvtra.iventoryservice.dto.response.BomResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.hvtra.iventoryservice.service.BomService;
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
            @RequestParam(required = false) Long productVariantId,
            @RequestParam(required = false) Integer version,
            @RequestParam(required = false) Status status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.findAll(productVariantId, version, status, pageable);
    }

    @GetMapping("/{id}")
    public BomResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BomResponse create(@Valid @RequestBody BomRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public BomResponse update(@PathVariable Long id, @Valid @RequestBody BomRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public BomResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
