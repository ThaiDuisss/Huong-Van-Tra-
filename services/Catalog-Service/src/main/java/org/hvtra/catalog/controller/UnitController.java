package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.service.UnitService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/units")
public class UnitController {
    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    @GetMapping
    public List<UnitResponse> findAll() {
        return service.findAll();
    }

    @PostMapping("/batch")
    public java.util.List<UnitResponse> findByIds(@RequestBody java.util.List<UUID> ids) {
        return service.findAllByIds(ids);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnitResponse create(@Valid @RequestBody UnitRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public UnitResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody UnitRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UnitResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
