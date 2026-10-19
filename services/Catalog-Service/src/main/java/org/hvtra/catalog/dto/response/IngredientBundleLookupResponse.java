package org.hvtra.catalog.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Nguyên liệu khách được chọn khi đóng trà theo yêu cầu trên POS: đang hoạt động, là nguyên liệu pha chế, có giá bán lẻ. */
public record IngredientBundleLookupResponse(
        UUID id,
        String ingredientCode,
        String name,
        String imageUrl,
        String unitSymbol,
        BigDecimal retailPricePerUnit
) {
}
