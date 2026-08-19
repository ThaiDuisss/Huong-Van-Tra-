package org.hvtra.pos.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogVariantResponse(
        UUID id,
        UUID productId,
        String productName,
        String productCode,
        String sku,
        String variantName,
        String categoryName,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        String imageUrl,
        UUID unitId,
        String inventoryPolicy,
        String status,
        String productStatus,
        boolean sellable
) {
}
