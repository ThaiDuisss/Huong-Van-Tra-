package org.hvtra.iventoryservice.mapper;

import org.hvtra.iventoryservice.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.entity.InventoryLocation;
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
        location.setBranchId(request.branchId());
        location.setParentLocation(parentLocation);
        location.setCode(request.code().trim());
        location.setName(request.name().trim());
        location.setType(request.type());
        location.setStatus(request.status());
    }
}
