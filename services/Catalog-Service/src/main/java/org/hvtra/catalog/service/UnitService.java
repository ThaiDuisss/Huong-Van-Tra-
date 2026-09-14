package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.enums.UnitType;

public interface UnitService {
    PageResponse<UnitResponse> findAll(String name, Status status, UnitType type, Pageable pageable);
    PageResponse<LookupResponse> lookup(String search, Pageable pageable);
    List<UnitResponse> findAll();
    List<UnitResponse> findBases();
    UnitResponse create(UnitRequest request);
    UnitResponse update(UUID id, UnitRequest request);
    UnitResponse updateStatus(UUID id, Status status);
    List<UnitResponse> findAllByIds(List<UUID> ids);
    void delete(UUID id);
    byte[] exportToExcel(String search, Status status, UnitType type);
    byte[] getImportTemplate();
    void importFromExcel(MultipartFile file);
}
