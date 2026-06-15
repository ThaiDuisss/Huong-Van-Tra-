package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.entity.Unit;
import org.springframework.stereotype.Component;

@Component
public class UnitMapper {
    public UnitResponse toResponse(Unit unit) {
        return new UnitResponse(
                unit.getId(), unit.getName(), unit.getSymbol(), unit.getPrecisionScale(), unit.getStatus());
    }

    public void update(Unit unit, UnitRequest request) {
        unit.setName(request.name().trim());
        unit.setSymbol(request.symbol().trim());
        unit.setPrecisionScale(request.precisionScale());
        unit.setStatus(request.status());
    }
}
