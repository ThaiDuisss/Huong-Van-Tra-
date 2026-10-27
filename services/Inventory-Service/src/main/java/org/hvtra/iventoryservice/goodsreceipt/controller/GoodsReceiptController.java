package org.hvtra.iventoryservice.goodsreceipt.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.goodsreceipt.dto.request.*;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptSummaryCountResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptExportResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptListItemResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptResponse;
import org.hvtra.iventoryservice.goodsreceipt.service.GoodsReceiptService;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
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
@RequestMapping("/api/inventory/goods-receipts")
public class GoodsReceiptController {

    private final GoodsReceiptService service;

    public GoodsReceiptController(GoodsReceiptService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<GoodsReceiptListItemResponse>> findAll(
            @RequestParam(required = false) InventoryDocumentStatus status,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.findAll(status, locationId, supplierId, query, fromDate, toDate, pageable));
    }

    /** Dữ liệu xuất Excel: toàn bộ phiếu khớp bộ lọc (không phân trang, tối đa 10.000 dòng). */
    @GetMapping("/export")
    public ResponseEntity<List<GoodsReceiptExportResponse>> export(
            @RequestParam(required = false) InventoryDocumentStatus status,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate
    ) {
        return ResponseEntity.ok(service.export(status, locationId, supplierId, query, fromDate, toDate));
    }

    @GetMapping("/summary-counts")
    public ResponseEntity<GoodsReceiptSummaryCountResponse> getSummaryCounts() {
        return ResponseEntity.ok(service.getSummaryCounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoodsReceiptResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @UserAction(value = "PURCHASE_RECEIPT_CREATE", entityType = "InventoryDocument", entityId = "#result.body.id")
    @PostMapping
    public ResponseEntity<GoodsReceiptResponse> createGoodsReceipt(
            @Valid @RequestBody CreateGoodsReceiptRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGoodsReceipt(request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_UPDATE", entityType = "InventoryDocument", entityId = "#id")
    @PutMapping("/{id}")
    public ResponseEntity<GoodsReceiptResponse> updateGoodsReceipt(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGoodsReceiptRequest request
    ) {
        return ResponseEntity.ok(service.updateGoodsReceipt(id, request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_SUBMIT", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/submit")
    public ResponseEntity<GoodsReceiptResponse> submitForApproval(@PathVariable UUID id) {
        return ResponseEntity.ok(service.submitForApproval(id));
    }

    @UserAction(value = "PURCHASE_RECEIPT_APPROVE", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/approve")
    public ResponseEntity<GoodsReceiptResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveReceiptRequest request
    ) {
        return ResponseEntity.ok(service.approve(id, request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_REJECT", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/reject")
    public ResponseEntity<GoodsReceiptResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectReceiptRequest request
    ) {
        return ResponseEntity.ok(service.reject(id, request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_START_RECEIVING", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/start-receiving")
    public ResponseEntity<GoodsReceiptResponse> startReceiving(@PathVariable UUID id) {
        return ResponseEntity.ok(service.startReceiving(id));
    }

    @UserAction(value = "PURCHASE_RECEIPT_QC", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/qc")
    public ResponseEntity<GoodsReceiptResponse> completeInspection(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteInspectionRequest request
    ) {
        return ResponseEntity.ok(service.completeInspection(id, request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_POST", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/post")
    public ResponseEntity<GoodsReceiptResponse> post(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ResolveReceiptExceptionRequest request
    ) {
        return ResponseEntity.ok(service.post(id, request));
    }

    @UserAction(value = "PURCHASE_RECEIPT_CANCEL", entityType = "InventoryDocument", entityId = "#id", detail = "#note")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<GoodsReceiptResponse> cancel(
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
