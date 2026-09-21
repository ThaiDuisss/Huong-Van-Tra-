package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface IngredientService {
    PageResponse<IngredientResponse> findAll(String search, Status status, Pageable pageable, UUID supplierId);
    IngredientResponse findById(UUID id);
    IngredientResponse create(IngredientRequest request);
    IngredientResponse update(UUID id, IngredientRequest request);
    IngredientResponse updateStatus(UUID id, Status status);
    List<IngredientResponse> findByIds(List<UUID> ids);
    List<UUID> searchIngredientIds(String keyword, String code, String name, UUID unitId);
    void delete(UUID id);
    byte[] exportToExcel(String search, Status status);
    byte[] getImportTemplate();
}

