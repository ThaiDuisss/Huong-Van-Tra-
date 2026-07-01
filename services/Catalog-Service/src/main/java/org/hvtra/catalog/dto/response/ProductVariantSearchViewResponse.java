package org.hvtra.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantSearchViewResponse(
        UUID productId,
        UUID variantId,
        String sku,
        String productName,
        String variantName,
        String categoryName,
        @JsonProperty("selling_price") BigDecimal sellingPrice,
        String imageUrl,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        String stockStatus
) {}
