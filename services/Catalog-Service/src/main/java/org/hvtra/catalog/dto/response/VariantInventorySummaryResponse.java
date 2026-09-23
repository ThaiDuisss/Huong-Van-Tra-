package org.hvtra.catalog.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record VariantInventorySummaryResponse(
        UUID id,
        String sku,
        String variantName,
        UUID productId,
        String productCode,
        String productName,
        UUID categoryId,
        String categoryCode,
        String categoryName,
        UUID unitId,
        String unitName,
        String unitSymbol,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        String imageUrl
) {}
