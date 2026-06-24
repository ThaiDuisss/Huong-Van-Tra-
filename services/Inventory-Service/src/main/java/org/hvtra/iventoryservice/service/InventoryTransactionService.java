package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.StockAdjustmentRequest;
import org.hvtra.iventoryservice.dto.request.StockInRequest;
import org.hvtra.iventoryservice.dto.response.InventoryTransactionResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.enums.InventoryTransactionType;
import org.springframework.data.domain.Pageable;

import org.hvtra.iventoryservice.dto.request.StoreStockAdjustmentRequest;
import org.hvtra.iventoryservice.dto.request.StoreStockInRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public interface InventoryTransactionService {
    InventoryTransactionResponse stockIn(StockInRequest request);
    InventoryTransactionResponse adjust(StockAdjustmentRequest request);
    InventoryTransactionResponse storeStockIn(StoreStockInRequest request);
    InventoryTransactionResponse storeAdjust(StoreStockAdjustmentRequest request);
    PageResponse<InventoryTransactionResponse> findAll(
            String transactionCode,
            InventoryTransactionType type,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String createdBy,
            UUID branchId,
            InventoryReferenceType referenceType,
            String referenceId,
            Pageable pageable);
    InventoryTransactionResponse findById(UUID id);
}
