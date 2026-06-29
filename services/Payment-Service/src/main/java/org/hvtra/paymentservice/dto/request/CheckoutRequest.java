package org.hvtra.paymentservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CheckoutRequest {

    @NotNull(message = "orderId không được null")
    private UUID orderId;

    @NotNull(message = "receivedAmount không được null")
    @DecimalMin(value = "0.00", inclusive = false, message = "receivedAmount phải lớn hơn 0")
    private BigDecimal receivedAmount;

    @NotNull(message = "Payment method not null")
    private String paymentMethod;

    private UUID createdBy;
}
