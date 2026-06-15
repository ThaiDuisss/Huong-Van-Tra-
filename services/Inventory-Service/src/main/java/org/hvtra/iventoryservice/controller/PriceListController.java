package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.PriceListRequest;
import org.hvtra.iventoryservice.dto.request.StatusRequest;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.dto.response.PriceListResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.hvtra.iventoryservice.service.PriceListService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/price-lists")
public class PriceListController {
    private final PriceListService service;

    public PriceListController(PriceListService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PriceListResponse> findAll(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveAt,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.findAll(branchId, status, effectiveAt, pageable);
    }

    @GetMapping("/{id}")
    public PriceListResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceListResponse create(@Valid @RequestBody PriceListRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PriceListResponse update(
            @PathVariable Long id, @Valid @RequestBody PriceListRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public PriceListResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
