package org.hvtra.orderservice.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hvtra.orderservice.dto.response.CatalogVariantResponse;
import org.hvtra.orderservice.dto.response.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * Lấy BOM (Bill of Materials) đang hoạt động của một Mặt hàng.
     * Dùng để tính toán nguyên liệu cần trừ kho khi đơn hàng hoàn thành.
     *
     * TODO: Endpoint này cần được implement trong Catalog-Service (BomController).
     */
    @GetMapping("/api/catalog/product-variants/{variantId}/active-bom")
    BomResponse getActiveBomByVariantId(@PathVariable("variantId") UUID variantId);

    record ProductVariantPosViewResponse(
            UUID productId,
            @JsonProperty("id") UUID variantId,
            String sku,
            String productName,
            String variantName,
            String categoryName,
            BigDecimal sellingPrice,
            String imageUrl
    ){}

    /**
     * Cấu trúc BOM trả về từ Catalog-Service.
     */
    record BomResponse(
            UUID id,
            UUID productVariantId,
            Integer bomVersion,
            String status,
            List<BomItemResponse> items
    ) {}

    record BomItemResponse(
            UUID id,
            UUID ingredientId,
            String ingredientName,
            /** Số lượng nguyên liệu cần dùng cho 1 đơn vị sản phẩm */
            BigDecimal quantity,
            UUID unitId,
            String unitSymbol,
            /** Tỷ lệ hao phí (ví dụ: 5% = 0.05) */
            BigDecimal wasteRate
    ) {}
}
