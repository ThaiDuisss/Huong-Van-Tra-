package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BomResponse(
        UUID id,
        UUID productVariantId,
        Integer version,
        Status status,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
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

