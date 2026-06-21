package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.BarcodeCreateRequest;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.catalog.service.BarcodeService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/product-variants")
public class ProductVariantController {
    private final ProductVariantService service;
    private final BarcodeService barcodeService;

    public ProductVariantController(ProductVariantService service, BarcodeService barcodeService) {
        this.service = service;
        this.barcodeService = barcodeService;
    }

    @GetMapping
    public PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "variantName", required = false) String variantName,
            @RequestParam(name = "barcode", required = false) String barcode,
            @RequestParam(name = "productId", required = false) Long productId,
            @RequestParam(name = "status", required = false) Status status,
            @PageableDefault(size = 20, sort = "sku") Pageable pageable) {
        return service.findAll(sku, variantName, barcode, productId, status, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse.VariantResponse findById(@PathVariable("id") Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public ProductResponse.VariantResponse update(
            @PathVariable("id") Long id, @Valid @RequestBody ProductVariantRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProductResponse.VariantResponse updateStatus(
            @PathVariable("id") Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @PostMapping("/{variantId}/barcodes")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse.BarcodeResponse createBarcode(
            @PathVariable("variantId") Long variantId,
            @Valid @RequestBody BarcodeCreateRequest request) {
        return barcodeService.create(variantId, request);
    }
}
