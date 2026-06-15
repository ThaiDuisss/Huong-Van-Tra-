package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductSummaryResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    PageResponse<ProductSummaryResponse> findAll(
            String name, Long categoryId, Status status, Pageable pageable);
    ProductResponse findById(Long id);
    ProductResponse create(ProductRequest request);
    ProductResponse update(Long id, ProductRequest request);
    ProductResponse updateStatus(Long id, Status status);
}
