package org.hvtra.orderservice.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogClient {

    @GetMapping("/api/catalog/product-variants/{variantId}/pos-view")
    ProductVariantPosViewResponse getProductVariantPosView(@PathVariable("variantId") UUID variantId);

    record ProductVariantPosViewResponse(
            UUID productId,
            UUID variantId,
            String sku,
            String productName,
            String variantName,
            String categoryName,
            @JsonProperty("selling_price") BigDecimal sellingPrice,
            String imageUrl
    ){}
}
