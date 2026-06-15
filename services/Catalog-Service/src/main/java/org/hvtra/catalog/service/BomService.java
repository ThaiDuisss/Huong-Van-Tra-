package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.BomRequest;
import org.hvtra.catalog.dto.response.BomResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

public interface BomService {
    PageResponse<BomResponse> findAll(
            Long productVariantId, Integer version, Status status, Pageable pageable);
    BomResponse findById(Long id);
    BomResponse create(BomRequest request);
    BomResponse update(Long id, BomRequest request);
    BomResponse updateStatus(Long id, Status status);
}
