package org.hvtra.iventoryservice.customerreturn.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnActualCondition;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCustomerReturnInspectionLineRequest(
        @NotNull UUID returnItemId,
        @NotNull @Positive BigDecimal quantity,
        @NotNull
        @Schema(implementation = String.class, allowableValues = {"RESTOCK", "DAMAGED", "WRITE_OFF"})
        CustomerReturnQcResult result,
        UUID destinationLocationId,
        @Size(max = 500) String note,
        CustomerReturnActualCondition actualCondition
) {
    // Order commands open drafts without a QC observation; posting requires one.
    public CreateCustomerReturnInspectionLineRequest(
            UUID returnItemId, BigDecimal quantity, CustomerReturnQcResult result,
            UUID destinationLocationId, String note) {
        this(returnItemId, quantity, result, destinationLocationId, note, null);
    }
}
