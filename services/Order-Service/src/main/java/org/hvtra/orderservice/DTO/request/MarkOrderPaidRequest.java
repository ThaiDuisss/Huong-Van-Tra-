package org.hvtra.orderservice.DTO.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class MarkOrderPaidRequest {

    @NotNull(message = "paidAmount không được null")
    @DecimalMin(value = "0.00", message = "paidAmount phải lớn hơn hoặc bằng 0")
    private BigDecimal paidAmount;
    private String paymentCode;
    private UUID changedBy;
}
