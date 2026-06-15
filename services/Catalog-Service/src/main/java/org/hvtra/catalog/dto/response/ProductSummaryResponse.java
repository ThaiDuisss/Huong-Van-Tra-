package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

public record ProductSummaryResponse(
        Long id,
        String name,
        CategoryResponse category,
        String imageUrl,
        Status status,
        int totalVariants
) {
}
