package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.enums.Status;

import java.util.List;
import java.util.UUID;

public interface UnitService {
    List<UnitResponse> findAll();
    UnitResponse create(UnitRequest request);
    UnitResponse update(UUID id, UnitRequest request);
    UnitResponse updateStatus(UUID id, Status status);
    java.util.List<UnitResponse> findAllByIds(java.util.List<UUID> ids);
}

