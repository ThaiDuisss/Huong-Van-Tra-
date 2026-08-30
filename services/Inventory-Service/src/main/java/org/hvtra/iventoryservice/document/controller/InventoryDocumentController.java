package org.hvtra.iventoryservice.document.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.document.dto.request.*;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/documents")
public class InventoryDocumentController {

    private final InventoryDocumentService service;

    public InventoryDocumentController(InventoryDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<InventoryDocumentResponse>> findAll(
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

    @GetMapping("/{id}")
    public ResponseEntity<InventoryDocumentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<InventoryDocumentResponse> createPurchaseReceipt(
            @Valid @RequestBody CreatePurchaseReceiptRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPurchaseReceipt(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryDocumentResponse> updatePurchaseReceipt(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePurchaseReceiptRequest request
    ) {
        return ResponseEntity.ok(service.updatePurchaseReceipt(id, request));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<InventoryDocumentResponse> submitForApproval(@PathVariable UUID id) {
        return ResponseEntity.ok(service.submitForApproval(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<InventoryDocumentResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveReceiptRequest request
    ) {
        return ResponseEntity.ok(service.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<InventoryDocumentResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectReceiptRequest request
    ) {
        return ResponseEntity.ok(service.reject(id, request));
    }

    @PostMapping("/{id}/start-receiving")
    public ResponseEntity<InventoryDocumentResponse> startReceiving(@PathVariable UUID id) {
        return ResponseEntity.ok(service.startReceiving(id));
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<InventoryDocumentResponse> confirmReceiving(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveReceiptRequest request
    ) {
        return ResponseEntity.ok(service.confirmReceiving(id, request));
    }

    @PostMapping("/{id}/qc")
    public ResponseEntity<InventoryDocumentResponse> completeQc(
            @PathVariable UUID id,
            @Valid @RequestBody QcReceiptRequest request
    ) {
        return ResponseEntity.ok(service.completeQc(id, request));
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<InventoryDocumentResponse> post(@PathVariable UUID id) {
        return ResponseEntity.ok(service.post(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<InventoryDocumentResponse> cancel(
            @PathVariable UUID id,
            @RequestParam(required = false) String note
    ) {
        return ResponseEntity.ok(service.cancel(id, note));
    }
}
