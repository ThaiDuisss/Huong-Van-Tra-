package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.StockAdjustmentRequest;
import org.hvtra.iventoryservice.dto.request.StockInRequest;
import org.hvtra.iventoryservice.dto.response.InventoryTransactionResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.shared.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.shared.enums.InventoryTransactionType;
import org.springframework.data.domain.Pageable;

import org.hvtra.iventoryservice.dto.request.StoreStockAdjustmentRequest;
import org.hvtra.iventoryservice.dto.request.StoreStockInRequest;
import org.hvtra.iventoryservice.dto.request.StoreStockOutRequest;
import org.hvtra.iventoryservice.dto.request.StoreTransferRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public interface InventoryTransactionService {
    InventoryTransactionResponse stockIn(StockInRequest request);
    InventoryTransactionResponse adjust(StockAdjustmentRequest request);
    InventoryTransactionResponse storeStockIn(StoreStockInRequest request);
    InventoryTransactionResponse storeAdjust(StoreStockAdjustmentRequest request);
    InventoryTransactionResponse storeStockOut(StoreStockOutRequest request);
    InventoryTransactionResponse storeTransfer(StoreTransferRequest request);
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
    PageResponse<InventoryTransactionResponse> findAllForStore(
            String transactionCode,
            InventoryTransactionType type,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String createdBy,
            InventoryReferenceType referenceType,
            String referenceId,
            Pageable pageable);
    InventoryTransactionResponse findById(UUID id);
}
