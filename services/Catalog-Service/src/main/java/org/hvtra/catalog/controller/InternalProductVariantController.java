package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.ProductVariantProductRefResponse;
import org.hvtra.catalog.service.ProductVariantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
