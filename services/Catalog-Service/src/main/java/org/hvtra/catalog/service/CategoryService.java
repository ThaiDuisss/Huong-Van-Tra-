package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.List;

public interface CategoryService {
    PageResponse<CategoryResponse> findAll(String name, Status status, Pageable pageable);
    CategoryResponse findById(UUID id);
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(UUID id, CategoryRequest request);
    CategoryResponse updateStatus(UUID id, Status status);
    List<CategoryResponse> findAllByIds(List<UUID> ids);
    PageResponse<LookupResponse> lookup(String search, Pageable pageable);
}

