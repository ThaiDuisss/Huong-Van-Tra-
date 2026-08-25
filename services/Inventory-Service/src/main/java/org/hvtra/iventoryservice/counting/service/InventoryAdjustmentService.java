package org.hvtra.iventoryservice.counting.service;

import org.hvtra.iventoryservice.counting.dto.request.CreateAdjustmentRequest;
import org.hvtra.iventoryservice.counting.dto.response.AdjustmentResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryAdjustmentService {
    PageResponse<AdjustmentResponse> findAll(UUID branchId, InventoryDocumentStatus status, Pageable pageable);
    AdjustmentResponse findById(UUID id);
    AdjustmentResponse create(CreateAdjustmentRequest request);
    AdjustmentResponse approve(UUID id);
    AdjustmentResponse post(UUID id);
    AdjustmentResponse reject(UUID id);
}
