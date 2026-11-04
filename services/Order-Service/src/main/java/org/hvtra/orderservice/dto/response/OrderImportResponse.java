package org.hvtra.orderservice.dto.response;

import java.util.List;

public record OrderImportResponse(
        boolean dryRun,
        int totalRows,
        int totalOrders,
        /** Số đơn hợp lệ (dryRun) hoặc đã tạo thành công. */
        int successCount,
        /** Số đơn đã nhập trước đó, bị bỏ qua. */
        int duplicateCount,
        int failedCount,
        List<OrderImportOrderResult> results
) {
}
