package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IngredientService {
    PageResponse<IngredientResponse> findAll(String search, Status status, Pageable pageable);
    IngredientResponse findById(UUID id);
    IngredientResponse create(IngredientRequest request);
    IngredientResponse update(UUID id, IngredientRequest request);
    IngredientResponse updateStatus(UUID id, Status status);
    void delete(UUID id);
    byte[] exportToExcel(String search, Status status);
    byte[] getImportTemplate();
    void importFromExcel(org.springframework.web.multipart.MultipartFile file);
}

