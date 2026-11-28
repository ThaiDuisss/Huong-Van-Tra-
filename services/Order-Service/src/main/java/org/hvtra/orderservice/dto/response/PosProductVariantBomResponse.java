package org.hvtra.orderservice.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** BOM đã quy đổi về đơn vị bán lẻ/tồn kho nguyên liệu để POS chuyển Product CUSTOMER thành CUSTOM_BUNDLE. */
public record PosProductVariantBomResponse(
        UUID variantId,
        String sku,
        String productName,
        String variantName,
        String productType,
        List<PosBundleBomLineResponse> lines
) {
    public record PosBundleBomLineResponse(
            UUID ingredientId,
            String ingredientCode,
            String ingredientName,
            String unitSymbol,
            BigDecimal quantity
    ) {
    }
}
