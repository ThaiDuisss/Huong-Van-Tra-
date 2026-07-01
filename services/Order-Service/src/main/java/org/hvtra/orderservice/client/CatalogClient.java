package org.hvtra.orderservice.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogClient {

    @GetMapping("/api/catalog/product-variants/{variantId}/pos-view")
    ProductVariantPosViewResponse getProductVariantPosView(@PathVariable("variantId") UUID variantId);

    /**
     * Lấy BOM (Bill of Materials) đang hoạt động của một biến thể sản phẩm.
     * Dùng để tính toán nguyên liệu cần trừ kho khi đơn hàng hoàn thành.
     *
     * TODO: Endpoint này cần được implement trong Catalog-Service (BomController).
     */
    @GetMapping("/api/catalog/product-variants/{variantId}/active-bom")
    BomResponse getActiveBomByVariantId(@PathVariable("variantId") UUID variantId);

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
