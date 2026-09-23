package org.hvtra.iventoryservice.report.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Builder
public record DisposalSummaryReportResponse(
        BigDecimal totalDisposalValue,
        long expiredBatchesCount,
        long damagedBatchesCount,
        long customerReturnsCount,
        long supplierReturnsCount,
        long writeOffDocumentsCount,
        List<DisposalItemDetail> items
) {
    public record DisposalItemDetail(
            String code,
            String name,
            String type,
            String sourceDocumentCode,
            BigDecimal quantity,
            String unitSymbol,
            BigDecimal estimatedValue,
            String reason,
            Instant date
    ) {}
}
