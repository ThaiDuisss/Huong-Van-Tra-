package org.hvtra.iventoryservice.replenishment.dto;

import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShelfReplenishmentResponse(
        UUID id,
        String documentCode,
        InventoryDocumentStatus status,
        UUID branchId,
        UUID warehouseId,
        String warehouseName,
        UUID shelfId,
        String shelfName,
        String referenceId,
        String note,
        BigDecimal totalQuantity,
        int lineCount,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        UUID postedBy,
        Instant postedAt,
        /** Phiếu do POS xuất kho ngay khi bán (không chờ kho xác nhận trước). */
        boolean autoIssued,
        /** Đã xuất hàng nhưng manager chưa duyệt lại. */
        boolean pendingReview,
        /** Người duyệt phiếu (hoặc manager duyệt lại phiếu POS xuất ngay). */
        UUID reviewedBy,
        Instant reviewedAt,
        List<ShelfReplenishmentLineResponse> lines,
        UUID rejectedBy,
        Instant rejectedAt,
        String rejectionReason
) {
}
