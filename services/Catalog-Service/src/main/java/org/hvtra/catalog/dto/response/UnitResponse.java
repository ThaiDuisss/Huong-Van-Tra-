package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.util.UUID;

public record UnitResponse(
        UUID id,
        String name,
        String symbol,
        Integer precisionScale,
        Status status
) {
}

