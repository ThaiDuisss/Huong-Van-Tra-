package org.hvtra.iventoryservice.customerreturn.dto.request;

import java.util.List;
import java.util.UUID;

public record PostCustomerReturnInspectionRequest(
        List<LineOverride> lines
) {
    public record LineOverride(
            UUID lineId,
            UUID returnItemId,
            UUID destinationLocationId
    ) {
    }
}
