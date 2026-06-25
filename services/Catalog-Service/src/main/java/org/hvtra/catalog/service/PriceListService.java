package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.PriceListRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.PriceListResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface PriceListService {
    PageResponse<PriceListResponse> findAll(
            UUID branchId, Status status, LocalDateTime effectiveAt, Pageable pageable);
    PriceListResponse findById(UUID id);
    PriceListResponse create(PriceListRequest request);
    PriceListResponse update(UUID id, PriceListRequest request);
    PriceListResponse updateStatus(UUID id, Status status);
}

