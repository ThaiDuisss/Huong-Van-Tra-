package org.hvtra.iventoryservice.goodsreceipt.dto.response;

public record GoodsReceiptSummaryCountResponse(
        long draft,
        long pendingApproval,
        long approved,
        long receiving,
        long pendingExceptionApproval,
        long awaitingQc,
        long readyToPost,
        long posted,
        long postedToday,
        long rejected,
        long cancelled,
        long total
) {
}
