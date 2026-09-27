package org.hvtra.orderservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PosProductVariantResponse(
        UUID productId,
        UUID variantId,
        String sku,
        String productName,
        String variantName,
        String categoryName,
        @JsonProperty("selling_price") BigDecimal sellingPrice,
        String imageUrl,
        UUID inventoryLocationId,
        BigDecimal sellableQuantity,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        String stockStatus
) {
}
