package org.hvtra.iventoryservice.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryDashboardSummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryExpiringBatchResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryExpiryQualitySummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryLocationSummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryTopSkuResponse;
import org.hvtra.iventoryservice.dashboard.service.InventoryDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/dashboards")
@Tag(name = "Inventory Dashboard API", description = "API tổng hợp chỉ số báo cáo và dữ liệu Dashboard tồn kho")
public class InventoryDashboardController {

    private final InventoryDashboardService service;

    public InventoryDashboardController(InventoryDashboardService service) {
        this.service = service;
    }

    @Operation(summary = "Lấy tổng quan chỉ số KPI kho", description = "Trả về tổng số SKU, tổng tồn vật lý, tổng tồn khả dụng, số hàng bị khóa, chứng từ chờ duyệt...")
    @GetMapping("/overview")
    public ResponseEntity<InventoryDashboardSummaryResponse> getOverviewSummary(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID locationId
    ) {
        return ResponseEntity.ok(service.getOverviewSummary(branchId, locationId));
    }

    @Operation(summary = "Lấy báo cáo phân rã tồn kho theo địa điểm", description = "Thống kê lượng tồn kho chi tiết theo từng khu vực kho/chi nhánh phục vụ biểu đồ Stacked Bar")
    @GetMapping("/location-breakdown")
    public ResponseEntity<List<InventoryLocationSummaryResponse>> getLocationBreakdown(
            @RequestParam(required = false) UUID branchId
    ) {
        return ResponseEntity.ok(service.getLocationBreakdown(branchId));
    }

    @Operation(summary = "Top sản phẩm tồn kho cao nhất", description = "Danh sách Top N sản phẩm có tồn vật lý và khả dụng lớn nhất")
    @GetMapping("/top-skus")
    public ResponseEntity<List<InventoryTopSkuResponse>> getTopSkus(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false, defaultValue = "6") int limit
    ) {
        return ResponseEntity.ok(service.getTopSkus(branchId, locationId, limit));
    }

    @Operation(summary = "Lấy báo cáo chất lượng & hạn dùng lô hàng", description = "Thống kê chỉ số các lô hàng cận hạn, đã hết hạn, quá hạn cách ly và giá trị hàng hỏng")
    @GetMapping("/expiry-quality")
    public ResponseEntity<InventoryExpiryQualitySummaryResponse> getExpiryQualitySummary(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID locationId
    ) {
        return ResponseEntity.ok(service.getExpiryQualitySummary(branchId, locationId));
    }

    @Operation(summary = "Lấy danh sách lô hàng sắp hết hạn (FEFO)", description = "Danh sách các lô hàng cận hạn hoặc đã hết hạn ưu tiên xuất kho trước")
    @GetMapping("/expiring-batches")
    public ResponseEntity<List<InventoryExpiringBatchResponse>> getExpiringBatches(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false, defaultValue = "30") int daysThreshold,
            @RequestParam(required = false, defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(service.getExpiringBatches(branchId, locationId, daysThreshold, limit));
    }
}
