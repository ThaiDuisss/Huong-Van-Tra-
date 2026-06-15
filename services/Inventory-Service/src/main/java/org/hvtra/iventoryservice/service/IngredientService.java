package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.IngredientRequest;
import org.hvtra.iventoryservice.dto.response.IngredientResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.springframework.data.domain.Pageable;

public interface IngredientService {
    PageResponse<IngredientResponse> findAll(String search, Status status, Pageable pageable);
    IngredientResponse findById(Long id);
    IngredientResponse create(IngredientRequest request);
    IngredientResponse update(Long id, IngredientRequest request);
    IngredientResponse updateStatus(Long id, Status status);
}
