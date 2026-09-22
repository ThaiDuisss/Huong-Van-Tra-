package org.hvtra.paymentservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RefundPaymentRequest {

    @NotNull(message = "returnId không được null")
    private UUID returnId;

    @NotNull(message = "amount không được null")
    @DecimalMin(value = "0.01", message = "amount phải lớn hơn 0")
    private BigDecimal amount;
}
