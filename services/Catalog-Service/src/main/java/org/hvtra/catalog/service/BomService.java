package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.BomRequest;
import org.hvtra.catalog.dto.response.BomResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BomService {
    PageResponse<BomResponse> findAll(
            UUID categoryId, UUID productVariantId, UUID productId, String search, Integer version, Status status, Pageable pageable);
    BomResponse findById(UUID id);
    BomResponse create(BomRequest request);
    BomResponse update(UUID id, BomRequest request);
    BomResponse updateStatus(UUID id, Status status);
}

