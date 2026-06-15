package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PriceListResponse(
        Long id,
        Long branchId,
        String name,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Status status,
        List<ItemResponse> items
) {
    public record ItemResponse(
            Long id,
            ProductResponse.VariantSummaryResponse productVariant,
            BigDecimal sellingPrice
    ) {
    }
}
