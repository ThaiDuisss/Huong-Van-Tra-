package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UnitService {
    PageResponse<UnitResponse> findAll(String name, Status status, org.hvtra.catalog.enums.UnitType type, Pageable pageable);
    List<UnitResponse> findAll();
    UnitResponse create(UnitRequest request);
    UnitResponse update(UUID id, UnitRequest request);
    UnitResponse updateStatus(UUID id, Status status);
    List<UnitResponse> findAllByIds(List<UUID> ids);
}
