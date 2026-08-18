package org.hvtra.iventoryservice.location.mapper;

import org.hvtra.iventoryservice.location.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.springframework.stereotype.Component;

@Component
public class InventoryLocationMapper {
    public InventoryLocationResponse toResponse(InventoryLocation location) {
        return new InventoryLocationResponse(
                location.getId(),
                location.getBranchId(),
                location.getParentLocation() == null ? null : location.getParentLocation().getId(),
                location.getCode(),
                location.getName(),
                location.getType(),
                location.getStatus()
        );
    }

    public void update(
            InventoryLocation location,
            InventoryLocationRequest request,
            InventoryLocation parentLocation) {
//        location.setBranchId(request.branchId());
        location.setParentLocation(parentLocation);
        location.setName(request.name().trim());
        location.setType(request.type());
        location.setStatus(request.status());
    }
}
