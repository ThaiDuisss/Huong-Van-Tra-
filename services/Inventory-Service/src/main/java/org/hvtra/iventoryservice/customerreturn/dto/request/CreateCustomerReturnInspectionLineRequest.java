package org.hvtra.iventoryservice.customerreturn.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCustomerReturnInspectionLineRequest(
        @NotNull UUID returnItemId,
        @NotNull @Positive BigDecimal quantity,
        @NotNull CustomerReturnQcResult result,
        UUID destinationLocationId,
        String note
) {
}
