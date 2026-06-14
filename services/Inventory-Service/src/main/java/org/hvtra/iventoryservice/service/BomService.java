package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.BomRequest;
import org.hvtra.iventoryservice.dto.response.BomResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.springframework.data.domain.Pageable;

public interface BomService {
    PageResponse<BomResponse> findAll(
            Long productVariantId, Integer version, Status status, Pageable pageable);
    BomResponse findById(Long id);
    BomResponse create(BomRequest request);
    BomResponse update(Long id, BomRequest request);
    BomResponse updateStatus(Long id, Status status);
}
