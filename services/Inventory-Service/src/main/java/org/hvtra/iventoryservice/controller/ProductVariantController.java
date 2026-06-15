package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.BarcodeCreateRequest;
import org.hvtra.iventoryservice.dto.request.ProductVariantRequest;
import org.hvtra.iventoryservice.dto.request.StatusRequest;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.dto.response.ProductResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.hvtra.iventoryservice.service.ProductVariantService;
import org.hvtra.iventoryservice.service.BarcodeService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product-variants")
public class ProductVariantController {
    private final ProductVariantService service;
    private final BarcodeService barcodeService;

    public ProductVariantController(ProductVariantService service, BarcodeService barcodeService) {
        this.service = service;
        this.barcodeService = barcodeService;
    }

    @GetMapping
    public PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String variantName,
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Status status,
            @PageableDefault(size = 20, sort = "sku") Pageable pageable) {
        return service.findAll(sku, variantName, barcode, productId, status, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse.VariantResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public ProductResponse.VariantResponse update(
            @PathVariable Long id, @Valid @RequestBody ProductVariantRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProductResponse.VariantResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @PostMapping("/{variantId}/barcodes")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse.BarcodeResponse createBarcode(
            @PathVariable Long variantId, @Valid @RequestBody BarcodeCreateRequest request) {
        return barcodeService.create(variantId, request);
    }
}
