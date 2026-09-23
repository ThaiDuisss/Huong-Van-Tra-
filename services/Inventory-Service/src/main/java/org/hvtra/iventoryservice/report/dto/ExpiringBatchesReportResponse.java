package org.hvtra.iventoryservice.report.dto;

import java.util.List;

public record ExpiringBatchesReportResponse(
        List<ExpiringBatchItemResponse> items,
        long totalExpiringCount,
        long expiredCount,
        long expiringWithin15DaysCount,
        long expiringWithin30DaysCount
) {}
