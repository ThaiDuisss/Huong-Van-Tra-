package org.hvtra.orderservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Catalog cho đóng trà theo yêu cầu: variant hệ thống "Trà đóng theo yêu cầu" và giá bán lẻ nguyên liệu. */
@FeignClient(name = "catalog-service", contextId = "customBundleCatalogClient")
public interface CustomBundleCatalogClient {

    /** Cùng dạng với GET /api/catalog/product-variants/{id}. */
    @GetMapping("/internal/catalog/product-variants/custom-bundle")
    CatalogClient.ProductVariantPosViewResponse getCustomBundleVariant();

    @PostMapping("/internal/catalog/ingredients/batch")
    List<IngredientResponse> getIngredientsByIds(@RequestBody List<UUID> ids);

    /**
     * @param type               1 = nguyên liệu pha chế, 0 = bao bì
     * @param retailPricePerUnit null = không bán lẻ trong bundle
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record IngredientResponse(
            UUID id,
            String ingredientCode,
            String name,
            String unitSymbol,
            Integer type,
            String status,
            BigDecimal retailPricePerUnit
    ) {
    }
}
