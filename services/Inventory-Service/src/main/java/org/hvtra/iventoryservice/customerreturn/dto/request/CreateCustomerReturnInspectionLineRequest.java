package org.hvtra.iventoryservice.customerreturn.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCustomerReturnInspectionLineRequest(
        @NotNull UUID returnItemId,
        @NotNull @Positive BigDecimal quantity,
        @NotNull
        @Schema(implementation = String.class, allowableValues = {"RESTOCK", "DAMAGED", "WRITE_OFF"})
        CustomerReturnQcResult result,
        UUID destinationLocationId,
        String note
) {
}
