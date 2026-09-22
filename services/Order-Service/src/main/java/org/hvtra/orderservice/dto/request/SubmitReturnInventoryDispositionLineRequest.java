package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitReturnInventoryDispositionLineRequest {

    @NotNull
    private UUID returnItemId;

    @NotNull
    @DecimalMin(value = "0.001", message = "Disposition quantity must be greater than 0")
    private BigDecimal quantity;

    @NotNull
    private ReturnInventoryDispositionResult result;

    private UUID destinationLocationId;

    @Size(max = 500)
    private String note;
}
