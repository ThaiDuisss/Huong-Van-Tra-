package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductVariantProductRefResponse;
import org.hvtra.catalog.dto.response.VariantCostResponse;
import org.hvtra.catalog.dto.response.VariantInventorySummaryResponse;
import org.hvtra.catalog.service.ProductVariantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/product-variants")
public class InternalProductVariantController {
    private final ProductVariantService service;

    public InternalProductVariantController(ProductVariantService service) {
        this.service = service;
    }

    @PostMapping("/by-products")
    public List<ProductVariantProductRefResponse> findRefsByProductIds(@RequestBody List<UUID> productIds) {
        return service.findRefsByProductIds(productIds);
    }

    @PostMapping("/batch")
    public List<ProductResponse.VariantResponse> findByIds(@RequestBody List<UUID> ids) {
        return service.findAllByIds(ids);
    }

    @PostMapping("/costs-batch")
    public List<VariantCostResponse> findCostPricesByIds(@RequestBody List<UUID> ids) {
        return service.findCostPricesByIds(ids);
    }

    @PostMapping("/inventory-summary-batch")
    public List<VariantInventorySummaryResponse> findInventorySummariesByIds(@RequestBody List<UUID> ids) {
        return service.findInventorySummariesByIds(ids);
    }

    @GetMapping("/search-ids")
    public List<UUID> searchIds(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productCode", required = false) String productCode,
            @RequestParam(name = "productName", required = false) String productName,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "unitId", required = false) UUID unitId) {
        return service.searchVariantIds(keyword, sku, productCode, productName, categoryCode, categoryId, unitId);
    }
}
