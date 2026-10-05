package org.hvtra.customer.dto.response;

import java.util.List;

public record CustomerImportResponse(
        boolean dryRun,
        int totalRows,
        /** Số dòng hợp lệ (dryRun) hoặc đã nhập thành công. */
        int successCount,
        int failedCount,
        List<CustomerImportRowResult> results
) {
}
