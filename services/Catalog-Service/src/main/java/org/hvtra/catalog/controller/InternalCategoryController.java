package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.service.CategoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/categories")
public class InternalCategoryController {
    private final CategoryService service;

    public InternalCategoryController(CategoryService service) {
        this.service = service;
    }

    @PostMapping("/batch")
    public List<CategoryResponse> findByIds(@RequestBody List<UUID> ids) {
        return service.findAllByIds(ids);
    }
}
