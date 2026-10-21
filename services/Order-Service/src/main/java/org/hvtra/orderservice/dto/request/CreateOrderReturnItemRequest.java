package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderReturnItemRequest {

    @NotNull
    private UUID orderItemId;

    @NotNull
    @DecimalMin(value = "0.001", message = "Return quantity must be greater than 0")
    private BigDecimal quantity;

    @NotNull
    private ReturnReason reason;

    @NotNull
    // Declared by the return creator; this is not a QC observation.
    private ReturnCondition condition;

    private String note;
}
