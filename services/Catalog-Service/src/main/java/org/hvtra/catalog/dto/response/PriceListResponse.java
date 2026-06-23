package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PriceListResponse(
        UUID id,
        UUID branchId,
        String name,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Status status,
        List<ItemResponse> items
) {
    public record ItemResponse(
            UUID id,
            ProductResponse.VariantSummaryResponse productVariant,
            BigDecimal sellingPrice
    ) {
    }
}

