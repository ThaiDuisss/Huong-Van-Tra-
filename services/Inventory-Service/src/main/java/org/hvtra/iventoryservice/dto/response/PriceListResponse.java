package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.Status;

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
