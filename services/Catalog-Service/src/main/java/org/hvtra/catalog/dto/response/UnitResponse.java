package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitResponse(
        UUID id,
        String name,
        String symbol,
        Integer precisionScale,
        UnitType type,
        UUID baseUnitId,
        BigDecimal conversionRate,
        Status status,
        /** Chỉ có ở API danh sách/chi tiết đơn vị (đã fetch sẵn đơn vị gốc), nơi khác là null. */
        String baseUnitName
) {
}

