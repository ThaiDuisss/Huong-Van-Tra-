package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.PriceListRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.PriceListResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.PriceListService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/catalog/price-lists")
public class PriceListController {
    private final PriceListService service;

    public PriceListController(PriceListService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PriceListResponse> findAll(
            @RequestParam(name = "branchId", required = false) Long branchId,
            @RequestParam(name = "status", required = false) Status status,
            @RequestParam(name = "effectiveAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveAt,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.findAll(branchId, status, effectiveAt, pageable);
    }

    @GetMapping("/{id}")
    public PriceListResponse findById(@PathVariable("id") Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceListResponse create(@Valid @RequestBody PriceListRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PriceListResponse update(
            @PathVariable("id") Long id, @Valid @RequestBody PriceListRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public PriceListResponse updateStatus(
            @PathVariable("id") Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
