package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.SupplierRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.SupplierResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SupplierService {
    PageResponse<SupplierResponse> findAll(String search, Status status, UUID ingredientId, Pageable pageable);
    SupplierResponse findById(UUID id);
    SupplierResponse create(SupplierRequest request);
    SupplierResponse update(UUID id, SupplierRequest request);
    SupplierResponse updateStatus(UUID id, Status status);
    void delete(UUID id);
}
