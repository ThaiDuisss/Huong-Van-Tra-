package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BomResponse(
        Long id,
        Long productVariantId,
        Integer version,
        Status status,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        List<ItemResponse> items
) {
    public record ItemResponse(
            Long id,
            IngredientResponse ingredient,
            BigDecimal quantity,
            UnitResponse unit,
            BigDecimal wasteRate
    ) {
    }
}
