package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.ProductRequest;
import org.hvtra.iventoryservice.dto.request.ProductVariantRequest;
import org.hvtra.iventoryservice.dto.request.StatusRequest;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.dto.response.ProductResponse;
import org.hvtra.iventoryservice.dto.response.ProductSummaryResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.hvtra.iventoryservice.service.ProductService;
import org.hvtra.iventoryservice.service.ProductVariantService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService service;
    private final ProductVariantService variantService;

    public ProductController(ProductService service, ProductVariantService variantService) {
        this.service = service;
        this.variantService = variantService;
    }

    @GetMapping
    public PageResponse<ProductSummaryResponse> findAll(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "status", required = false) Status status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.findAll(name, categoryId, status, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable("id") Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable("id") Long id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProductResponse updateStatus(
            @PathVariable("id") Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @PostMapping("/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse.VariantResponse createVariant(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody ProductVariantRequest request) {
        return variantService.create(productId, request);
    }
}
