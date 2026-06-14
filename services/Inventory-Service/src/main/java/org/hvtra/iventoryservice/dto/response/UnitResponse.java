package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.Status;

public record UnitResponse(
        Long id,
        String name,
        String symbol,
        Integer precisionScale,
        Status status
) {
}
