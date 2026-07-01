package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String productCode,
        String name,
        CategoryResponse category,
        String imageUrl,
        Status status,
        int totalVariants,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        String stockStatus,
        Boolean stockAvailable
) {
}
