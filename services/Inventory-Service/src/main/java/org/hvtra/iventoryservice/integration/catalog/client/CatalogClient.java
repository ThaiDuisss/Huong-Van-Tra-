package org.hvtra.iventoryservice.integration.catalog.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    @PostMapping("/internal/catalog/products/batch")
    List<ProductResponse> getProductsBatch(@RequestBody List<UUID> ids);

    @PostMapping("/internal/catalog/product-variants/batch")
    List<VariantResponse> getVariantsBatch(@RequestBody List<UUID> ids);

    @PostMapping("/internal/catalog/product-variants/by-products")
    List<ProductVariantProductRefResponse> getVariantRefsByProductIds(@RequestBody List<UUID> productIds);

    @PostMapping("/internal/catalog/categories/batch")
    List<CategoryResponse> getCategoriesBatch(@RequestBody List<UUID> ids);

    @PostMapping("/internal/catalog/units/batch")
    List<UnitResponse> getUnitsBatch(@RequestBody List<UUID> ids);

    @GetMapping("/internal/catalog/product-variants/search-ids")
    List<UUID> searchVariantIds(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productCode", required = false) String productCode,
            @RequestParam(name = "productName", required = false) String productName,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "unitId", required = false) UUID unitId
    );

    @PostMapping("/internal/catalog/ingredients/batch")
    List<IngredientResponse> getIngredientsBatch(@RequestBody List<UUID> ids);

    /** Tạo nguyên liệu mới bên Catalog-Service — dùng lúc import Excel phiếu nhập kho gặp mã nguyên
     * liệu chưa tồn tại. Catalog-Service tự đồng bộ InventoryItem tương ứng ngay trong lúc tạo.
     * {@code code} null/rỗng thì Catalog-Service tự sinh mã, không thì dùng đúng mã truyền vào. */
    @PostMapping("/internal/catalog/ingredients")
    IngredientResponse createIngredient(
            @RequestParam(name = "code", required = false) String code,
            @RequestBody CreateIngredientRequest request);

    record CreateIngredientRequest(String name, UUID unitId, Integer reorderPoint, String status) {}


    @GetMapping("/internal/catalog/ingredients/search-ids")
    List<UUID> searchIngredientIds(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "unitId", required = false) UUID unitId
    );

    @GetMapping("/api/catalog/suppliers/{id}")
    SupplierResponse getSupplier(@PathVariable("id") UUID id);

    @PostMapping("/internal/catalog/suppliers/batch")
    List<SupplierResponse> getSuppliersBatch(@RequestBody List<UUID> ids);

    /** Toàn bộ nhà cung cấp (id/code/name) — dùng để resolve theo mã lúc parse Excel phiếu nhập kho. */
    @GetMapping("/internal/catalog/suppliers")
    List<SupplierLookupResponse> getAllSuppliers();

    /** Bổ sung danh sách nguyên liệu được phép cung cấp cho nhà cung cấp. */
    @PostMapping("/internal/catalog/suppliers/{supplierId}/ingredients")
    void addIngredientsToSupplier(@PathVariable("supplierId") UUID supplierId, @RequestBody List<UUID> ingredientIds);

    /** Toàn bộ đơn vị — dùng để resolve theo tên/ký hiệu lúc tạo nguyên liệu mới từ Excel phiếu nhập kho. */
    @GetMapping("/internal/catalog/units")
    List<UnitResponse> getAllUnits();

    @GetMapping("/internal/catalog/product-variants/{variantId}/bom-items")
    List<BomItemResponse> getBomItems(@PathVariable("variantId") UUID variantId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BomItemResponse(
            UUID id,
            IngredientRef ingredient,
            BigDecimal quantity,
            UnitRef unit,
            BigDecimal wasteRate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IngredientRef(UUID id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnitRef(UUID id, String name, String symbol) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SupplierLookupResponse(
            UUID id,
            String code,
            String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SupplierResponse(
            UUID id,
            String supplierCode,
            String name,
            String phone,
            String email,
            String address,
            String taxCode,
            Object status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductResponse(
            UUID id,
            CategoryResponse category,
            String productCode,
            String name,
            String description,
            String imageUrl,
            String status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VariantResponse(
            UUID id,
            UUID productId,
            String productName,
            String productCode,
            String sku,
            String variantName,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            String imageUrl,
            String categoryName,
            UUID unitId,
            String unitName,
            String unitSymbol,
            String status,
            String productStatus,
            boolean sellable
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IngredientResponse(
            UUID id,
            String ingredientCode,
            String name,
            String imageUrl,
            UUID unitId,
            String unitName,
            String unitSymbol,
            Integer reorderPoint,
            Object status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductVariantProductRefResponse(
            UUID id,
            UUID productId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CategoryResponse(
            UUID id,
            UUID parentId,
            String name,
            String status,
            String categoryCode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnitResponse(
            UUID id,
            String name,
            String symbol,
            Integer precisionScale,
            String status
    ) {}
}
