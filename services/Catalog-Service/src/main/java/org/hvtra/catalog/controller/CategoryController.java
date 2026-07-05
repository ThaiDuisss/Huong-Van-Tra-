package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.CategoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CategoryResponse> findAll(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) Status status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.findAll(name, status, pageable);
    }

    @GetMapping("/lookup")
    public PageResponse<LookupResponse> lookup(
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.lookup(search, pageable);
    }

    @GetMapping("/{id}")
    public CategoryResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @PostMapping("/batch")
    public java.util.List<CategoryResponse> findByIds(@RequestBody java.util.List<UUID> ids) {
        return service.findAllByIds(ids);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody CategoryRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public CategoryResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
