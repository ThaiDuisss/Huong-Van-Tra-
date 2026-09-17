package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductSummaryResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import org.hvtra.catalog.dto.response.LookupResponse;
import java.util.List;

public interface ProductService {
    PageResponse<ProductResponse> findAll(
            String name, UUID categoryId, Status status, Pageable pageable);
    PageResponse<LookupResponse> lookup(String search, Pageable pageable);
    ProductResponse findById(UUID id);
    ProductResponse create(ProductRequest request);
    ProductResponse update(UUID id, ProductRequest request);
    ProductResponse updateStatus(UUID id, Status status);
    byte[] exportToExcel();
    byte[] getImportTemplate();
    List<ProductResponse> findAllByIds(List<UUID> ids);
    void delete(UUID id);
}

