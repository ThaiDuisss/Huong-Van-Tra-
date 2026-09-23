package org.hvtra.iventoryservice.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hvtra.iventoryservice.report.dto.*;
import org.hvtra.iventoryservice.report.service.InventoryReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/reports")
@Tag(name = "Inventory Reports API", description = "API các báo cáo tổng hợp tồn kho, giá trị, vòng quay, hao hụt và tiêu hủy")
public class InventoryReportController {

    private final InventoryReportService service;

    public InventoryReportController(InventoryReportService service) {
        this.service = service;
    }

    @Operation(summary = "Báo cáo giá trị tồn kho", description = "Tổng hợp tổng giá trị vốn đọng theo nhóm sản phẩm và kho")
    @GetMapping("/inventory-value")
    public ResponseEntity<InventoryValueReportResponse> getInventoryValueReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String categoryCode
    ) {
        return ResponseEntity.ok(service.getInventoryValueReport(fromDate, toDate, locationId, categoryCode));
    }

    @Operation(summary = "Báo cáo hiệu suất luân chuyển tồn kho", description = "Tính hệ số luân chuyển kho, số ngày tồn kho DSI và phân nhóm mặt hàng")
    @GetMapping("/turnover")
    public ResponseEntity<InventoryTurnoverReportResponse> getInventoryTurnoverReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String categoryCode
    ) {
        return ResponseEntity.ok(service.getInventoryTurnoverReport(fromDate, toDate, locationId, categoryCode));
    }

    @Operation(summary = "Báo cáo hao hụt sản xuất", description = "Thống kê lượng nguyên liệu định mức vs thực tế xuất và tỷ lệ hao hụt")
    @GetMapping("/production-wastage")
    public ResponseEntity<ProductionWastageReportResponse> getProductionWastageReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return ResponseEntity.ok(service.getProductionWastageReport(fromDate, toDate));
    }

    @Operation(summary = "Báo cáo tổng hợp hàng hỏng, trả lại & tiêu hủy", description = "Tổng hợp các khoản tổn thất do tiêu hủy, trả hàng ncc/khách và hàng hết hạn")
    @GetMapping("/disposal-summary")
    public ResponseEntity<DisposalSummaryReportResponse> getDisposalSummaryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID locationId
    ) {
        return ResponseEntity.ok(service.getDisposalSummaryReport(fromDate, toDate, locationId));
    }

    @Operation(summary = "Báo cáo hàng đang vận chuyển (In-Transit)", description = "Thống kê các đơn chuyển kho đang trên đường luân chuyển")
    @GetMapping("/in-transit-summary")
    public ResponseEntity<InTransitSummaryResponse> getInTransitSummary() {
        return ResponseEntity.ok(service.getInTransitSummary());
    }

    @Operation(summary = "Báo cáo tồn kho lâu ngày (Aged Inventory)", description = "Lọc danh sách các lô hàng/sản phẩm có tuổi tồn kho vượt ngưỡng")
    @GetMapping("/aged-inventory")
    public ResponseEntity<AgedInventoryReportResponse> getAgedInventoryReport(
            @RequestParam(required = false, defaultValue = "90") Integer minAgeInDays,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        return ResponseEntity.ok(service.getAgedInventoryReport(minAgeInDays, page, size));
    }

    @Operation(summary = "Báo cáo lô hàng cận đát / hết hạn", description = "Lọc các lô hàng có hạn sử dụng cận ngày hoặc đã hết hạn")
    @GetMapping("/expiring-batches")
    public ResponseEntity<ExpiringBatchesReportResponse> getExpiringBatchesReport(
            @RequestParam(required = false, defaultValue = "30") Integer daysToExpiry,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        return ResponseEntity.ok(service.getExpiringBatchesReport(daysToExpiry, page, size));
    }
}

