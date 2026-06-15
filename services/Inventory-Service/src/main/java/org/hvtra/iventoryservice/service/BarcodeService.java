package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.BarcodeCreateRequest;
import org.hvtra.iventoryservice.dto.request.BarcodeUpdateRequest;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

public interface BarcodeService {
    PageResponse<ProductResponse.BarcodeResponse> findAll(String value, Pageable pageable);
    ProductResponse.VariantResponse scan(String value);
    ProductResponse.BarcodeResponse create(Long variantId, BarcodeCreateRequest request);
    ProductResponse.BarcodeResponse update(Long id, BarcodeUpdateRequest request);
    void delete(Long id);
}
