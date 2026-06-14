package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.UnitRequest;
import org.hvtra.iventoryservice.dto.response.UnitResponse;
import org.hvtra.iventoryservice.enums.Status;

import java.util.List;

public interface UnitService {
    List<UnitResponse> findAll();
    UnitResponse create(UnitRequest request);
    UnitResponse update(Long id, UnitRequest request);
    UnitResponse updateStatus(Long id, Status status);
}
