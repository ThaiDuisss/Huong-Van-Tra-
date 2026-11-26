package org.hvtra.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.BusinessReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.CustomerReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.IngredientAnalysisReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductFormulaReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductReport;
import org.hvtra.orderservice.service.ManagementReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders/reports/management")
@RequiredArgsConstructor
public class ManagementReportController {

    private final ManagementReportService managementReportService;

    @Operation(summary = "Báo cáo kinh doanh tổng hợp và so sánh kỳ")
    @GetMapping("/business")
    public BusinessReport getBusinessReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID customerId
    ) {
        return managementReportService.getBusinessReport(fromDate, toDate, productId, categoryId, customerId);
    }

    @Operation(summary = "Báo cáo sản phẩm: bán chạy, doanh thu cao, tăng trưởng, suy giảm và xu hướng")
    @GetMapping("/products")
    public ProductReport getProductReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return managementReportService.getProductReport(fromDate, toDate, productId, categoryId, customerId, limit);
    }

    @Operation(summary = "Báo cáo khách hàng: top mua nhiều, doanh thu cao, tần suất và lịch sử")
    @GetMapping("/customers")
    public CustomerReport getCustomerReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return managementReportService.getCustomerReport(fromDate, toDate, productId, categoryId, customerId, limit);
    }

    @Operation(summary = "Báo cáo phân tích sản phẩm và công thức BOM (phân trang)")
    @GetMapping("/product-formulas")
    public ProductFormulaReport getProductFormulaReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return managementReportService.getProductFormulaReport(fromDate, toDate, categoryId, productId, page, size);
    }

    @Operation(summary = "Báo cáo phân tích nguyên liệu và cặp nguyên liệu kết hợp (phân trang)")
    @GetMapping("/ingredient-analysis")
    public IngredientAnalysisReport getIngredientAnalysisReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return managementReportService.getIngredientAnalysisReport(fromDate, toDate, categoryId, productId, page, size);
    }
}
