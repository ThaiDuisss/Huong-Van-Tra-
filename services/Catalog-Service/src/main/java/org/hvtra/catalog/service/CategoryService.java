package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CategoryService {
    PageResponse<CategoryResponse> findAll(String name, Status status, Pageable pageable);
    CategoryResponse findById(UUID id);
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(UUID id, CategoryRequest request);
    CategoryResponse updateStatus(UUID id, Status status);
}

