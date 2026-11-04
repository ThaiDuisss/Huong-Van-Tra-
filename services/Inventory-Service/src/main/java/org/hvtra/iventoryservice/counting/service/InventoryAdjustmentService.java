package org.hvtra.iventoryservice.counting.service;

import org.hvtra.iventoryservice.counting.dto.request.CreateAdjustmentRequest;
import org.hvtra.iventoryservice.counting.dto.response.AdjustmentResponse;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.document.InventoryAdjustment;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryAdjustmentService {
    PageResponse<AdjustmentResponse> findAll(
            UUID branchId, InventoryDocumentStatus status, String keyword, Pageable pageable);
    AdjustmentResponse findById(UUID id);
    AdjustmentResponse create(CreateAdjustmentRequest request);
    InventoryAdjustment createAndPostFromCount(InventoryCount count, UUID approvedBy);
    AdjustmentResponse approve(UUID id);
    AdjustmentResponse post(UUID id);
    AdjustmentResponse reject(UUID id);
}
