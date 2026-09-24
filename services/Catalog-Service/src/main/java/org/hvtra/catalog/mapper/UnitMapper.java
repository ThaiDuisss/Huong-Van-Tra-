package org.hvtra.catalog.mapper;

import jakarta.persistence.EntityNotFoundException;
import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.entity.Unit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class UnitMapper {
    public UnitResponse toResponse(Unit unit) {
        if (unit == null) {
            return null;
        }
        try {
            return new UnitResponse(
                    unit.getId(),
                    unit.getName(),
                    unit.getSymbol(),
                    unit.getPrecisionScale(),
                    unit.getType(),
                    unit.getBaseUnit() != null ? unit.getBaseUnit().getId() : null,
                    unit.getConversionRate(),
                    unit.getStatus(),
                    null
            );
        } catch (EntityNotFoundException e) {
            return null;
        }
    }

    /** Kèm tên đơn vị gốc — chỉ gọi khi baseUnit đã được fetch sẵn (tránh lazy-load từng dòng). */
    public UnitResponse toResponseWithBaseUnitName(Unit unit) {
        UnitResponse response = toResponse(unit);
        if (response == null || unit.getBaseUnit() == null) return response;
        return new UnitResponse(response.id(), response.name(), response.symbol(), response.precisionScale(),
                response.type(), response.baseUnitId(), response.conversionRate(), response.status(),
                unit.getBaseUnit().getName());
    }

    /**
     * Cập nhật các field của Unit từ request.
     * @param resolvedBaseUnit Entity đơn vị gốc đã được resolve (có thể null nếu không chọn đơn vị gốc)
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
