package org.hvtra.iventoryservice.goodsreceipt.service;

import org.hvtra.iventoryservice.goodsreceipt.dto.request.*;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptSummaryCountResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptExportResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptListItemResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GoodsReceiptService {

    PageResponse<GoodsReceiptListItemResponse> findAll(
            InventoryDocumentStatus status,
            UUID locationId,
            UUID supplierId,
            String query,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    );

    List<GoodsReceiptExportResponse> export(
            InventoryDocumentStatus status,
            UUID locationId,
            UUID supplierId,
            String query,
            Instant fromDate,
            Instant toDate
    );

    GoodsReceiptSummaryCountResponse getSummaryCounts();

    GoodsReceiptResponse findById(UUID id);

    GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request);

    GoodsReceiptResponse updateGoodsReceipt(UUID id, UpdateGoodsReceiptRequest request);

    GoodsReceiptResponse submitForApproval(UUID id);

    GoodsReceiptResponse approve(UUID id, ApproveReceiptRequest request);

    GoodsReceiptResponse reject(UUID id, RejectReceiptRequest request);

    GoodsReceiptResponse startReceiving(UUID id);

    GoodsReceiptResponse confirmReceiving(UUID id, ReceiveReceiptRequest request);

    GoodsReceiptResponse completeQc(UUID id, QcReceiptRequest request);

    GoodsReceiptResponse completeInspection(UUID id, CompleteInspectionRequest request);

    GoodsReceiptResponse post(UUID id, ResolveReceiptExceptionRequest request);

    GoodsReceiptResponse cancel(UUID id, String note);

    void delete(UUID id);
}
