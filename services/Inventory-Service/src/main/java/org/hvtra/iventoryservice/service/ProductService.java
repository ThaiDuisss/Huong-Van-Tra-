package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.ProductRequest;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.dto.response.ProductResponse;
import org.hvtra.iventoryservice.dto.response.ProductSummaryResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    PageResponse<ProductSummaryResponse> findAll(
            String name, Long categoryId, Status status, Pageable pageable);
    ProductResponse findById(Long id);
    ProductResponse create(ProductRequest request);
    ProductResponse update(Long id, ProductRequest request);
    ProductResponse updateStatus(Long id, Status status);
}
