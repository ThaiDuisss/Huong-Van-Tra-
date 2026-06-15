package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.StockAdjustmentRequest;
import org.hvtra.iventoryservice.dto.request.StockInRequest;
import org.hvtra.iventoryservice.dto.response.InventoryTransactionResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.enums.InventoryTransactionType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface InventoryTransactionService {
    InventoryTransactionResponse stockIn(StockInRequest request);
    InventoryTransactionResponse adjust(StockAdjustmentRequest request);
    PageResponse<InventoryTransactionResponse> findAll(
            String transactionCode,
            InventoryTransactionType type,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Long createdBy,
            Long branchId,
            InventoryReferenceType referenceType,
            Long referenceId,
            Pageable pageable);
    InventoryTransactionResponse findById(Long id);
}
