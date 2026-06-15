package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BomResponse(
        Long id,
        ProductResponse.VariantSummaryResponse productVariant,
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
