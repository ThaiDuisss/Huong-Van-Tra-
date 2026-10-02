package org.hvtra.iventoryservice.replenishment.dto;

/** Số phiếu theo trạng thái, dùng cho thẻ tổng quan trên màn danh sách. */
public record ShelfReplenishmentSummaryResponse(
        long draft,
        long pending,
        long posted,
        long cancelled,
        /** Đã xuất hàng (POS xuất ngay khi bán) nhưng manager chưa duyệt. */
        long pendingReview
) {
}
