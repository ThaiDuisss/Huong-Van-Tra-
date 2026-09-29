package org.hvtra.iventoryservice.customerreturn.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CustomerReturnQcDispositionReportRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CustomerReturnQcSummaryBatchRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.PostCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcDispositionReportResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcSummaryResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.iventoryservice.customerreturn.service.CustomerReturnInspectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class CustomerReturnInspectionController {

    private final CustomerReturnInspectionService service;

    @PostMapping("/customer-returns/{returnId}/inspections")
    public ResponseEntity<CustomerReturnInspectionResponse> createInspection(
            @PathVariable UUID returnId,
            @Valid @RequestBody CreateCustomerReturnInspectionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createInspection(returnId, request));
    }

    @PostMapping("/customer-return-inspections/{inspectionId}/post")
    public ResponseEntity<CustomerReturnInspectionResponse> postInspection(
            @PathVariable UUID inspectionId,
            @RequestBody(required = false) PostCustomerReturnInspectionRequest request
    ) {
        return ResponseEntity.ok(service.postInspection(inspectionId, request));
    }

    @GetMapping("/customer-return-inspections/{inspectionId}")
    public ResponseEntity<CustomerReturnInspectionResponse> getInspection(@PathVariable UUID inspectionId) {
        return ResponseEntity.ok(service.getInspection(inspectionId));
    }

    @GetMapping("/customer-return-inspections")
    public ResponseEntity<Page<CustomerReturnInspectionResponse>> listInspections(
            @RequestParam(required = false) String returnCode,
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) CustomerReturnInspectionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(service.listInspections(returnCode, orderCode, status, fromDate, toDate, pageable));
    }

    @GetMapping("/customer-returns/{returnId}/qc-summary")
    public ResponseEntity<CustomerReturnQcSummaryResponse> getQcSummary(@PathVariable UUID returnId) {
        return ResponseEntity.ok(service.getQcSummary(returnId));
    }

    @PostMapping("/customer-returns/qc-summaries")
    public ResponseEntity<List<CustomerReturnQcSummaryResponse>> getQcSummaries(
            @Valid @RequestBody CustomerReturnQcSummaryBatchRequest request
    ) {
        return ResponseEntity.ok(service.getQcSummaries(request));
    }

    @PostMapping("/customer-returns/qc-disposition-report")
    public ResponseEntity<List<CustomerReturnQcDispositionReportResponse>> getQcDispositionReport(
            @Valid @RequestBody CustomerReturnQcDispositionReportRequest request
    ) {
        return ResponseEntity.ok(service.getQcDispositionReport(request));
    }

    @GetMapping("/internal/return-dispositions/{commandId}/status")
    public ResponseEntity<ReturnDispositionStatusResponse> getDispositionStatus(
            @PathVariable UUID commandId
    ) {
        return ResponseEntity.ok(service.getDispositionStatus(commandId));
    }
}
