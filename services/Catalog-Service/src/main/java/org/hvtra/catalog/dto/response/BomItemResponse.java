package org.hvtra.catalog.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BomItemResponse(
        UUID id,
        IngredientResponse ingredient,
        BigDecimal quantity,
        UnitResponse unit,
        BigDecimal wasteRate
) {
}
