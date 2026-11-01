package org.hvtra.iventoryservice.goodsreceipt.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Một dòng dữ liệu xuất Excel danh sách phiếu. */
public record GoodsReceiptExportResponse(
        String documentCode,
        String referenceId,
        String supplierName,
        String locationName,
        InventoryDocumentStatus status,
        long totalLines,
        BigDecimal totalReceivedQuantity,
        BigDecimal totalAcceptedQuantity,
        BigDecimal totalRejectedQuantity,
        Instant receivedAt,
        Instant createdAt,
        String createdByName
) {
}
