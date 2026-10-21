package org.hvtra.iventoryservice.customerreturn.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnActualCondition;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;

import java.util.List;
import java.util.UUID;

public record PostCustomerReturnInspectionRequest(
        @Valid List<LineOverride> lines
) {
    public record LineOverride(
            UUID lineId,
            UUID returnItemId,
            UUID destinationLocationId,
            CustomerReturnActualCondition actualCondition,
            CustomerReturnQcResult result,
            @Size(max = 500) String note
    ) {
        public LineOverride(UUID lineId, UUID returnItemId, UUID destinationLocationId) {
            this(lineId, returnItemId, destinationLocationId, null, null, null);
        }
    }
}
