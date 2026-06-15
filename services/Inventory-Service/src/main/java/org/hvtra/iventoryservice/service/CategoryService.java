package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.CategoryRequest;
import org.hvtra.iventoryservice.dto.response.CategoryResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.springframework.data.domain.Pageable;

public interface CategoryService {
    PageResponse<CategoryResponse> findAll(String name, Status status, Pageable pageable);
    CategoryResponse findById(Long id);
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(Long id, CategoryRequest request);
    CategoryResponse updateStatus(Long id, Status status);
}
