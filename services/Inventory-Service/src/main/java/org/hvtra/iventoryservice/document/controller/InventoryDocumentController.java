package org.hvtra.iventoryservice.document.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.document.dto.request.*;
import org.hvtra.iventoryservice.document.dto.response.GoodsReceiptSummaryCountResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentExportResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentListItemResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentResponse;
import org.hvtra.iventoryservice.document.service.InventoryDocumentService;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/documents")
public class InventoryDocumentController {

    private final InventoryDocumentService service;

    public InventoryDocumentController(InventoryDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<InventoryDocumentListItemResponse>> findAll(
            @RequestParam(required = false) InventoryDocumentType documentType,
            @RequestParam(required = false) InventoryDocumentStatus status,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.findAll(documentType, status, locationId, supplierId, query, fromDate, toDate, pageable));
    }

    /** Dữ liệu xuất Excel: toàn bộ phiếu khớp bộ lọc (không phân trang, tối đa 10.000 dòng). */
    @GetMapping("/export")
    public ResponseEntity<List<InventoryDocumentExportResponse>> export(
            @RequestParam(required = false) InventoryDocumentType documentType,
            @RequestParam(required = false) InventoryDocumentStatus status,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate
    ) {
        return ResponseEntity.ok(service.export(documentType, status, locationId, supplierId, query, fromDate, toDate));
    }

    @GetMapping("/summary-counts")
    public ResponseEntity<GoodsReceiptSummaryCountResponse> getSummaryCounts(
            @RequestParam(required = false, defaultValue = "PURCHASE_RECEIPT") InventoryDocumentType documentType
    ) {
        return ResponseEntity.ok(service.getSummaryCounts(documentType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryDocumentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @UserAction(value = "PURCHASE_RECEIPT_CREATE", entityType = "InventoryDocument", entityId = "#result.body.id")
    @PostMapping
    public ResponseEntity<InventoryDocumentResponse> createPurchaseReceipt(
            @Valid @RequestBody CreatePurchaseReceiptRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPurchaseReceipt(request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_UPDATE", entityType = "InventoryDocument", entityId = "#id")
    @PutMapping("/{id}")
    public ResponseEntity<InventoryDocumentResponse> updatePurchaseReceipt(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePurchaseReceiptRequest request
    ) {
        return ResponseEntity.ok(service.updatePurchaseReceipt(id, request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_SUBMIT", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/submit")
    public ResponseEntity<InventoryDocumentResponse> submitForApproval(@PathVariable UUID id) {
        return ResponseEntity.ok(service.submitForApproval(id));
    }

    @UserAction(value = "PURCHASE_RECEIPT_APPROVE", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/approve")
    public ResponseEntity<InventoryDocumentResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveReceiptRequest request
    ) {
        return ResponseEntity.ok(service.approve(id, request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_REJECT", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/reject")
    public ResponseEntity<InventoryDocumentResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectReceiptRequest request
    ) {
        return ResponseEntity.ok(service.reject(id, request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_START_RECEIVING", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/start-receiving")
    public ResponseEntity<InventoryDocumentResponse> startReceiving(@PathVariable UUID id) {
        return ResponseEntity.ok(service.startReceiving(id));
    }

    @UserAction(value = "PURCHASE_RECEIPT_QC", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/qc")
    public ResponseEntity<InventoryDocumentResponse> completeInspection(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteInspectionRequest request
    ) {
        return ResponseEntity.ok(service.completeInspection(id, request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_POST", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/post")
    public ResponseEntity<InventoryDocumentResponse> post(@PathVariable UUID id) {
        return ResponseEntity.ok(service.post(id));
    }

    @UserAction(value = "PURCHASE_RECEIPT_CANCEL", entityType = "InventoryDocument", entityId = "#id", detail = "#note")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<InventoryDocumentResponse> cancel(
            @PathVariable UUID id,
            @RequestParam(required = false) String note
    ) {
        return ResponseEntity.ok(service.cancel(id, note));
    }

    @UserAction(value = "PURCHASE_RECEIPT_DELETE", entityType = "InventoryDocument", entityId = "#id")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
