package org.hvtra.iventoryservice.document.dto.response;

public record GoodsReceiptSummaryCountResponse(
        long draft,
        long pendingApproval,
        long receiving,
        long awaitingQc,
        long readyToPost,
        long postedToday,
        long total
) {
}
