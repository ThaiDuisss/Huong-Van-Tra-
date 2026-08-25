package org.hvtra.iventoryservice.integration.catalog.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogClient {

    @GetMapping("/api/catalog/products/{id}")
    ProductResponse getProduct(@PathVariable("id") UUID id);

    @GetMapping("/api/catalog/product-variants/{id}")
    VariantResponse getVariant(@PathVariable("id") UUID id);

    @GetMapping("/api/catalog/categories/{id}")
    CategoryResponse getCategory(@PathVariable("id") UUID id);

    @GetMapping("/api/catalog/units/{id}")
    UnitResponse getUnit(@PathVariable("id") UUID id);

    @PostMapping("/api/catalog/products/batch")
    List<ProductResponse> getProductsBatch(@RequestBody List<UUID> ids);

    @PostMapping("/api/catalog/product-variants/batch")
    List<VariantResponse> getVariantsBatch(@RequestBody List<UUID> ids);

    @PostMapping("/internal/catalog/product-variants/by-products")
    List<ProductVariantProductRefResponse> getVariantRefsByProductIds(@RequestBody List<UUID> productIds);

    @PostMapping("/api/catalog/categories/batch")
    List<CategoryResponse> getCategoriesBatch(@RequestBody List<UUID> ids);

    @PostMapping("/api/catalog/units/batch")
    List<UnitResponse> getUnitsBatch(@RequestBody List<UUID> ids);

    @GetMapping("/api/catalog/product-variants/search-ids")
    List<UUID> searchVariantIds(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productCode", required = false) String productCode,
            @RequestParam(name = "productName", required = false) String productName,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "unitId", required = false) UUID unitId
    );

    record ProductResponse(
            UUID id,
            CategoryResponse category,
            String productCode,
            String name,
            String description,
            String imageUrl,
            String status
    ) {}

    record VariantResponse(
            UUID id,
            UUID productId,
            String productName,
            String productCode,
            String sku,
            String variantName,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            UUID unitId,
            String status,
            String productStatus,
            boolean sellable
    ) {}

    record ProductVariantProductRefResponse(
            UUID id,
            UUID productId
    ) {}

    record CategoryResponse(
            UUID id,
            UUID parentId,
            String name,
            String status,
            String categoryCode
    ) {}

    record UnitResponse(
            UUID id,
            String name,
            String symbol,
            Integer precisionScale,
            String status
    ) {}
}
