package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.PriceListRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.PriceChangeHistoryResponse;
import org.hvtra.catalog.dto.response.PriceListResponse;
import org.hvtra.catalog.enums.PriceChangeType;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.PriceChangeHistoryService;
import org.hvtra.catalog.service.PriceListService;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/price-lists")
public class PriceListController {
    private final PriceListService service;
    private final PriceChangeHistoryService historyService;

    public PriceListController(PriceListService service, PriceChangeHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }

    @GetMapping
    public PageResponse<PriceListResponse> findAll(
            @RequestParam(name = "branchId", required = false) UUID branchId,
            @RequestParam(name = "status", required = false) Status status,
            @RequestParam(name = "effectiveAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveAt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(branchId, status, effectiveAt, pageable);
    }

    @GetMapping("/{id}")
    public PriceListResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    /** Lịch sử thay đổi giá của một bảng giá, mới nhất trước. from/to là ISO-8601 có múi giờ (vd 2026-10-01T00:00:00Z). */
    @GetMapping("/{id}/history")
    public PageResponse<PriceChangeHistoryResponse> findHistory(
            @PathVariable("id") UUID id,
            @RequestParam(name = "variantId", required = false) UUID variantId,
            @RequestParam(name = "changeType", required = false) PriceChangeType changeType,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return historyService.findByPriceList(id, variantId, changeType, from, to, page, size);
    }

    /** Lịch sử thay đổi giá của một Mặt hàng trên mọi bảng giá, mới nhất trước. */
    @GetMapping("/history")
    public PageResponse<PriceChangeHistoryResponse> findVariantHistory(
            @RequestParam(name = "variantId") UUID variantId,
            @RequestParam(name = "changeType", required = false) PriceChangeType changeType,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return historyService.findByVariant(variantId, changeType, from, to, page, size);
    }

    @UserAction(value = "PRICE_LIST_CREATE", entityType = "PriceList", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceListResponse create(@Valid @RequestBody PriceListRequest request) {
        return service.create(request);
    }

    @UserAction(value = "PRICE_LIST_UPDATE", entityType = "PriceList", entityId = "#id")
    @PutMapping("/{id}")
    public PriceListResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody PriceListRequest request) {
        return service.update(id, request);
    }

    @UserAction(value = "PRICE_LIST_STATUS_UPDATE", entityType = "PriceList", entityId = "#id", detail = "#request.status")
    @PatchMapping("/{id}/status")
    public PriceListResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
