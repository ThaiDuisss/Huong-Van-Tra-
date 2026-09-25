package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.CategoryService;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

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
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(name, status, pageable);
    }

    @GetMapping("/lookup")
    public PageResponse<LookupResponse> lookup(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "ids", required = false) List<UUID> ids,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.lookup(search, ids, pageable);
    }

    @GetMapping("/{id}")
    public CategoryResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @PostMapping("/batch")
    public List<CategoryResponse> findByIds(@RequestBody List<UUID> ids) {
        return service.findAllByIds(ids);
    }

    @UserAction(value = "CATEGORY_CREATE", entityType = "Category", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return service.create(request);
    }

    @UserAction(value = "CATEGORY_UPDATE", entityType = "Category", entityId = "#id")
    @PutMapping("/{id}")
    public CategoryResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody CategoryRequest request) {
        return service.update(id, request);
    }

    @UserAction(value = "CATEGORY_STATUS_UPDATE", entityType = "Category", entityId = "#id", detail = "#request.status")
    @PatchMapping("/{id}/status")
    public CategoryResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @UserAction(value = "CATEGORY_DELETE", entityType = "Category", entityId = "#id")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }
}
