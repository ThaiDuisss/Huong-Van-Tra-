package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

public interface IngredientService {
    PageResponse<IngredientResponse> findAll(String search, Status status, Pageable pageable);
    IngredientResponse findById(Long id);
    IngredientResponse create(IngredientRequest request);
    IngredientResponse update(Long id, IngredientRequest request);
    IngredientResponse updateStatus(Long id, Status status);
}
