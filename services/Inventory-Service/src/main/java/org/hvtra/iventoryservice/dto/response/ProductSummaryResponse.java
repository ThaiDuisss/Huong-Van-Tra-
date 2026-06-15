package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.Status;

public record ProductSummaryResponse(
        Long id,
        String name,
        CategoryResponse category,
        String imageUrl,
        Status status,
        int totalVariants
) {
}
