package org.hvtra.iventoryservice.report.service;

import org.hvtra.iventoryservice.report.dto.*;

import java.time.LocalDate;
import java.util.UUID;

public interface InventoryReportService {
    InventoryValueReportResponse getInventoryValueReport(LocalDate fromDate, LocalDate toDate, UUID locationId, String categoryCode);
    InventoryTurnoverReportResponse getInventoryTurnoverReport(LocalDate fromDate, LocalDate toDate, UUID locationId, String categoryCode);
    ProductionWastageReportResponse getProductionWastageReport(LocalDate fromDate, LocalDate toDate);
    DisposalSummaryReportResponse getDisposalSummaryReport(LocalDate fromDate, LocalDate toDate, UUID locationId);
    InTransitSummaryResponse getInTransitSummary();
    AgedInventoryReportResponse getAgedInventoryReport(Integer minAgeInDays, Integer page, Integer size);
    ExpiringBatchesReportResponse getExpiringBatchesReport(Integer daysToExpiry, Integer page, Integer size);
}
