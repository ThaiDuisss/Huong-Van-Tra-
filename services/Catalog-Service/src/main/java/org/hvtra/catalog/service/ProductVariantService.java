package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

public interface ProductVariantService {
    PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            String sku, String variantName, String barcode, Long productId, Status status, Pageable pageable);
    ProductResponse.VariantResponse findById(Long id);
    ProductResponse.VariantResponse create(Long productId, ProductVariantRequest request);
    ProductResponse.VariantResponse update(Long id, ProductVariantRequest request);
    ProductResponse.VariantResponse updateStatus(Long id, Status status);
}
