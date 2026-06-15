package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

public record UnitResponse(
        Long id,
        String name,
        String symbol,
        Integer precisionScale,
        Status status
) {
}
