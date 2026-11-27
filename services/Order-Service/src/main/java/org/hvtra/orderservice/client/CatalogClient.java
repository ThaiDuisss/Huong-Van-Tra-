package org.hvtra.orderservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hvtra.orderservice.dto.response.CatalogVariantResponse;
import org.hvtra.orderservice.dto.response.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/api/catalog/product-variants")
    PageResponse<CatalogVariantResponse> findSellableVariants(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productId", required = false) UUID productId,
            @RequestParam(name = "sellableOnly") boolean sellableOnly,
            @RequestParam(name = "page") int page,
            @RequestParam(name = "size") int size
    );

    @GetMapping("/api/catalog/product-variants/{id}")
    CatalogVariantResponse findById(@PathVariable("id") UUID id);

    @GetMapping("/api/catalog/product-variants/{variantId}")
    ProductVariantPosViewResponse getProductVariantPosView(@PathVariable("variantId") UUID variantId);

    @GetMapping("/internal/catalog/product-variants/{variantId}/bom-items")
    List<BomItemResponse> getBomItemsByVariantId(@PathVariable("variantId") UUID variantId);

    @PostMapping("/internal/catalog/bom-items/batch")
    Map<UUID, List<BomItemResponse>> getBomItemsByVariantIds(@RequestBody List<UUID> variantIds);

    /** Cấu hình tồn mục tiêu / ngưỡng sản xuất của mặt hàng (chỉ đọc, không bị trừ khi bán). */
    @GetMapping("/api/catalog/product-variants/{variantId}")
    VariantStockConfigResponse getVariantStockConfig(@PathVariable("variantId") UUID variantId);

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductVariantPosViewResponse(
            UUID productId,
            @JsonProperty("id") UUID variantId,
            String sku,
            String productName,
            String variantName,
            String categoryName,
            BigDecimal sellingPrice,
            BigDecimal sellableQuantity,
            String imageUrl,
            String productType
    ) {
        public ProductVariantPosViewResponse(
                UUID productId,
                UUID variantId,
                String sku,
                String productName,
                String variantName,
                String categoryName,
                BigDecimal sellingPrice,
                BigDecimal sellableQuantity,
                String imageUrl) {
            this(productId, variantId, sku, productName, variantName, categoryName, sellingPrice, sellableQuantity,
                    imageUrl, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BomItemResponse(
            UUID id,
            BomIngredientResponse ingredient,
            BigDecimal quantity,
            BomUnitResponse unit,
            BigDecimal wasteRate
    ) {
        public BomItemResponse(UUID ingredientId, BigDecimal quantity, BigDecimal wasteRate) {
            this(null, new BomIngredientResponse(ingredientId), quantity, null, wasteRate);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BomIngredientResponse(
            UUID id,
            String ingredientCode,
            String name,
            UUID unitId,
            String unitName,
            String unitSymbol
    ) {
        public BomIngredientResponse(UUID id) {
            this(id, null, null, null, null, null);
        }

        public BomIngredientResponse(UUID id, String ingredientCode, String name) {
            this(id, ingredientCode, name, null, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BomUnitResponse(
            UUID id,
            String name,
            String symbol,
            String type,
            UUID baseUnitId,
            BigDecimal conversionRate
    ) {
        public BomUnitResponse(UUID id, String symbol, String name) {
            this(id, name, symbol, null, null, null);
        }
    }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VariantStockConfigResponse(
            UUID id,
            BigDecimal sellableQuantity,
            BigDecimal productionThreshold,
            BigDecimal productionBatchQuantity
    ) {
    }
}
