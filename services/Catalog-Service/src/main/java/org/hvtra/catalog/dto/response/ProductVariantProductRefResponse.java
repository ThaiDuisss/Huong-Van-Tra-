package org.hvtra.catalog.dto.response;

import java.util.UUID;

public record ProductVariantProductRefResponse(
        UUID id,
        UUID productId
) {
}
