package org.hvtra.orderservice.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hvtra.orderservice.dto.response.CatalogVariantResponse;
import org.hvtra.orderservice.dto.response.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "catalog-service")
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

    @GetMapping("/api/catalog/product-variants/{variantId}")
    ProductVariantPosViewResponse getProductVariantPosView(@PathVariable("variantId") UUID variantId);

    @GetMapping("/internal/catalog/product-variants/{variantId}/bom-items")
    List<BomItemResponse> getBomItemsByVariantId(@PathVariable("variantId") UUID variantId);

    @PatchMapping("/internal/catalog/product-variants/{variantId}/sellable/decrement")
    SellableQuantityAdjustmentResponse decrementSellableQuantity(
            @PathVariable("variantId") UUID variantId,
            @RequestBody SellableQuantityAdjustmentRequest request
    );

    @PatchMapping("/internal/catalog/product-variants/{variantId}/sellable/restore")
    SellableQuantityAdjustmentResponse restoreSellableQuantity(
            @PathVariable("variantId") UUID variantId,
            @RequestBody SellableQuantityAdjustmentRequest request
    );

    record ProductVariantPosViewResponse(
            UUID productId,
            @JsonProperty("id") UUID variantId,
            String sku,
            String productName,
            String variantName,
            String categoryName,
            BigDecimal sellingPrice,
            BigDecimal sellableQuantity,
            String imageUrl
    ){}

    record BomItemResponse(
            UUID id,
            BomIngredientResponse ingredient,
            BigDecimal quantity,
            Object unit,
            BigDecimal wasteRate
    ) {
        public BomItemResponse(UUID ingredientId, BigDecimal quantity, BigDecimal wasteRate) {
            this(null, new BomIngredientResponse(ingredientId), quantity, null, wasteRate);
        }
    }

    record BomIngredientResponse(UUID id) {}

    record SellableQuantityAdjustmentRequest(
            BigDecimal quantity,
            UUID locationId,
            String reason,
            String operationKey
    ) {}

    record SellableQuantityAdjustmentResponse(
            UUID productVariantId,
            BigDecimal requestedQuantity,
            BigDecimal oldSellableQuantity,
            BigDecimal newSellableQuantity,
            boolean thresholdCrossed,
            BigDecimal productionThreshold,
            BigDecimal productionBatchQuantity,
            UUID locationId,
            String reason
    ) {}
}
