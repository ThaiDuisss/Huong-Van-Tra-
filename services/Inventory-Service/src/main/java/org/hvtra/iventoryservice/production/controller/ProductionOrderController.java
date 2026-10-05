package org.hvtra.iventoryservice.production.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.production.dto.request.CancelProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.request.CreateProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.hvtra.iventoryservice.production.service.ProductionOrderService;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/production-orders")
public class ProductionOrderController {

    private final ProductionOrderService service;

    public ProductionOrderController(ProductionOrderService service) {
        this.service = service;
    }

    @GetMapping("/statuses")
    public ResponseEntity<List<String>> getAvailableStatuses() {
        return ResponseEntity.ok(Arrays.stream(ProductionOrderStatus.values())
                .map(Enum::name)
                .toList());
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductionOrderResponse>> findAll(
            @RequestParam(required = false) ProductionOrderStatus status,
            @RequestParam(required = false) UUID productVariantId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.findAll(status, productVariantId, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductionOrderResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductionOrderResponse> create(@Valid @RequestBody CreateProductionOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ProductionOrderResponse> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ProductionOrderResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ProductionOrderResponse> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(service.reject(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ProductionOrderResponse> cancel(
            @PathVariable UUID id, @Valid @RequestBody CancelProductionOrderRequest request) {
        return ResponseEntity.ok(service.cancel(id, request));
    }
}
