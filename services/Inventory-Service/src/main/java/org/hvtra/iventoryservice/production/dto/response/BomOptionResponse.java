package org.hvtra.iventoryservice.production.dto.response;

import java.util.UUID;

public record BomOptionResponse(
        UUID id,
        String bomCode,
        String name,
        boolean isDefault,
        Integer bomVersion
) {
}
