package org.hvtra.iventoryservice.production.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.production.dto.request.CancelProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.request.CompleteProductionOrderRequest;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/production-orders")
public class ProductionOrderController {

    private final ProductionOrderService service;

    public ProductionOrderController(ProductionOrderService service) {
        this.service = service;
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

    @PostMapping("/{id}/release")
    public ResponseEntity<ProductionOrderResponse> release(@PathVariable UUID id) {
        return ResponseEntity.ok(service.release(id));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ProductionOrderResponse> start(@PathVariable UUID id) {
        return ResponseEntity.ok(service.start(id));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ProductionOrderResponse> complete(
            @PathVariable UUID id, @Valid @RequestBody CompleteProductionOrderRequest request) {
        return ResponseEntity.ok(service.complete(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ProductionOrderResponse> cancel(
            @PathVariable UUID id, @Valid @RequestBody CancelProductionOrderRequest request) {
        return ResponseEntity.ok(service.cancel(id, request));
    }
}
