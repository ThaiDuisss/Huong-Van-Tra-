package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BomResponse(
        UUID id,
        UUID productVariantId,
        String variantSku,
        String variantName,
        String productName,
        String name,
        String bomCode,
        boolean isDefault,
        Integer version,
        Status status,
        String description,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        List<ItemResponse> items
) {
    public record ItemResponse(
            UUID id,
            IngredientResponse ingredient,
            BigDecimal quantity,
            UnitResponse unit,
            BigDecimal wasteRate
    ) {
    }
}

