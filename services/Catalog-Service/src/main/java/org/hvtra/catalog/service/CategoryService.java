package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

public interface CategoryService {
    PageResponse<CategoryResponse> findAll(String name, Status status, Pageable pageable);
    CategoryResponse findById(Long id);
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(Long id, CategoryRequest request);
    CategoryResponse updateStatus(Long id, Status status);
}
