package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String name,
        CategoryResponse category,
        String imageUrl,
        Status status,
        int totalVariants
) {
}

