package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductSummaryResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {
    PageResponse<ProductSummaryResponse> findAll(
            String name, UUID categoryId, Status status, Pageable pageable);
    ProductResponse findById(UUID id);
    ProductResponse create(ProductRequest request);
    ProductResponse update(UUID id, ProductRequest request);
    ProductResponse updateStatus(UUID id, Status status);
}

