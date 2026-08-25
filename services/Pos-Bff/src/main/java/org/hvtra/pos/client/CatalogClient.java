package org.hvtra.pos.client;

import org.hvtra.pos.dto.response.CatalogVariantResponse;
import org.hvtra.pos.dto.response.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "catalog-service", url = "${clients.catalog.url:}")
public interface CatalogClient {

    @GetMapping("/api/catalog/product-variants")
    PageResponse<CatalogVariantResponse> findAll(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "variantName", required = false) String variantName,
            @RequestParam(name = "barcode", required = false) String barcode,
            @RequestParam(name = "productId", required = false) UUID productId,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "100") int size
    );

    @GetMapping("/api/catalog/product-variants/{id}")
    CatalogVariantResponse findById(@PathVariable("id") UUID id);
}
