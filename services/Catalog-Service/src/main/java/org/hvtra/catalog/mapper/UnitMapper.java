package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.entity.Unit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class UnitMapper {
    public UnitResponse toResponse(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getSymbol(),
                unit.getPrecisionScale(),
                unit.getType(),
                unit.getBaseUnit() != null ? unit.getBaseUnit().getId() : null,
                unit.getConversionRate(),
                unit.getStatus()
        );
    }

    /**
     * Cập nhật các field của Unit từ request.
     * @param resolvedBaseUnit Entity đơn vị gốc đã được resolve (có thể null nếu type = BASE)
     */
    public void update(Unit unit, UnitRequest request, Unit resolvedBaseUnit) {
        unit.setName(request.name().trim());
        unit.setSymbol(request.symbol().trim());
        unit.setPrecisionScale(request.precisionScale());
        unit.setType(request.type());
        unit.setBaseUnit(resolvedBaseUnit);
        unit.setConversionRate(
                request.conversionRate() != null ? request.conversionRate() : BigDecimal.ONE
        );
        unit.setStatus(request.status());
    }
}
