package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.PriceListRequest;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.dto.response.PriceListResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface PriceListService {
    PageResponse<PriceListResponse> findAll(
            Long branchId, Status status, LocalDateTime effectiveAt, Pageable pageable);
    PriceListResponse findById(Long id);
    PriceListResponse create(PriceListRequest request);
    PriceListResponse update(Long id, PriceListRequest request);
    PriceListResponse updateStatus(Long id, Status status);
}
