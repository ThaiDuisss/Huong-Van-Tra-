package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.enums.Status;

import java.util.List;

public interface UnitService {
    List<UnitResponse> findAll();
    UnitResponse create(UnitRequest request);
    UnitResponse update(Long id, UnitRequest request);
    UnitResponse updateStatus(Long id, Status status);
}
