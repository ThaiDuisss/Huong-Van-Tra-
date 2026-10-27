package org.hvtra.iventoryservice.replenishment.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.replenishment.dto.CreateShelfReplenishmentRequest;
import org.hvtra.iventoryservice.replenishment.dto.RejectShelfReplenishmentRequest;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentResponse;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentSummaryResponse;
import org.hvtra.iventoryservice.replenishment.dto.UpdateShelfReplenishmentRequest;
import org.hvtra.iventoryservice.replenishment.service.ShelfReplenishmentService;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Phiếu nhập kệ: chuyển hàng từ kho lên kệ bán. */
@RestController
@RequestMapping("/api/inventory/shelf-replenishments")
public class ShelfReplenishmentController {
    private final ShelfReplenishmentService service;

    public ShelfReplenishmentController(ShelfReplenishmentService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ShelfReplenishmentResponse> findAll(
            @RequestParam(name = "status", required = false) InventoryDocumentStatus status,
            @RequestParam(name = "shelfId", required = false) UUID shelfId,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "pendingReview", required = false) Boolean pendingReview,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(status, shelfId, query, pendingReview, pageable);
    }

    @GetMapping("/summary")
    public ShelfReplenishmentSummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/{id}")
    public ShelfReplenishmentResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @UserAction(value = "SHELF_REPLENISHMENT_CREATE", entityType = "InventoryDocument", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShelfReplenishmentResponse create(@Valid @RequestBody CreateShelfReplenishmentRequest request) {
        return service.create(request);
    }

    @UserAction(value = "SHELF_REPLENISHMENT_UPDATE", entityType = "InventoryDocument", entityId = "#id")
    @PutMapping("/{id}")
    public ShelfReplenishmentResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody UpdateShelfReplenishmentRequest request) {
        return service.update(id, request);
    }

    @UserAction(value = "SHELF_REPLENISHMENT_SUBMIT", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/submit")
    public ShelfReplenishmentResponse submit(@PathVariable("id") UUID id) {
        return service.submit(id);
    }

    /** Duyệt phiếu: trừ kho, cộng kệ theo FEFO. Người duyệt phải khác người tạo. */
    @UserAction(value = "SHELF_REPLENISHMENT_APPROVE", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/approve")
    public ShelfReplenishmentResponse approve(@PathVariable("id") UUID id) {
        return service.approve(id);
    }

    @UserAction(value = "SHELF_REPLENISHMENT_REJECT", entityType = "InventoryDocument", entityId = "#id",
            detail = "#request.reason")
    @PostMapping("/{id}/reject")
    public ShelfReplenishmentResponse reject(
            @PathVariable("id") UUID id, @Valid @RequestBody RejectShelfReplenishmentRequest request) {
        return service.reject(id, request);
    }

    /** Manager duyệt lại phiếu đã được POS xuất kho ngay khi bán. */
    @UserAction(value = "SHELF_REPLENISHMENT_REVIEW", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/review")
    public ShelfReplenishmentResponse review(@PathVariable("id") UUID id) {
        return service.review(id);
    }

    @UserAction(value = "SHELF_REPLENISHMENT_CANCEL", entityType = "InventoryDocument", entityId = "#id")
    @PostMapping("/{id}/cancel")
    public ShelfReplenishmentResponse cancel(
            @PathVariable("id") UUID id, @RequestParam(name = "note", required = false) String note) {
        return service.cancel(id, note);
    }
}
