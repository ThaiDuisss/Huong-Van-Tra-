package org.hvtra.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.dto.response.TopSalesStaffReportResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductReportResponse;
import org.hvtra.orderservice.service.OrderStatisticReportExportService;
import org.hvtra.orderservice.service.OrderStatisticReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/orders/reports")
@RequiredArgsConstructor
public class OrderStatisticReportController {

    private static final DateTimeFormatter EXPORT_FILENAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final OrderStatisticReportService orderStatisticReportService;
    private final OrderStatisticReportExportService orderStatisticReportExportService;

    @Operation(summary = "bao cao nhan vien ban hang tot nhat")
    @GetMapping("/top-sales-staff")
    public TopSalesStaffReportResponse getTopSalesStaff(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = OrderStatisticReportService.SORT_REVENUE) String sortBy,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return orderStatisticReportService.getTopSalesStaff(fromDate, toDate, sortBy, limit);
    }

    @Operation(summary = "xuat excel bao cao hieu suat nhan vien ban hang")
    @GetMapping("/top-sales-staff/export")
    public ResponseEntity<byte[]> exportTopSalesStaff(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = OrderStatisticReportService.SORT_REVENUE) String sortBy,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return excelResponse(
                orderStatisticReportExportService.exportTopSalesStaff(fromDate, toDate, sortBy, limit),
                "bao-cao-nhan-vien-ban-hang"
        );
    }

    @Operation(summary = "bao cao san pham ban chay")
    @GetMapping("/top-selling-products")
    public TopSellingProductReportResponse getTopSellingProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = OrderStatisticReportService.SORT_QUANTITY) String sortBy,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return orderStatisticReportService.getTopSellingProducts(fromDate, toDate, sortBy, limit);
    }

    @Operation(summary = "xuat excel bao cao san pham ban chay kem ton kho")
    @GetMapping("/top-selling-products/export")
    public ResponseEntity<byte[]> exportTopSellingProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = OrderStatisticReportService.SORT_QUANTITY) String sortBy,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return excelResponse(
                orderStatisticReportExportService.exportTopSellingProducts(fromDate, toDate, sortBy, limit),
                "bao-cao-san-pham-ban-chay-ton-kho"
        );
    }

    private ResponseEntity<byte[]> excelResponse(byte[] excelBytes, String filenamePrefix) {
        String filename = filenamePrefix + "-" + LocalDateTime.now().format(EXPORT_FILENAME_TIME) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(XLSX_MEDIA_TYPE)
                .body(excelBytes);
    }
}
