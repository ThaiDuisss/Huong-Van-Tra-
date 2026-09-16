package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.util.UUID;

public record BomLookupResponse(
        UUID id,
        String bomCode,
        String name,
        boolean isDefault,
        Integer bomVersion,
        Status status
) {
}
