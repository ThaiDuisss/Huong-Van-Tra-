package org.hvtra.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CashPaymentResponse {

    private UUID paymentId;
    private String paymentCode;
    private UUID orderId;

    private BigDecimal amountRequired;
    private BigDecimal amountPaid;
    private BigDecimal receivedAmount;
    private BigDecimal changeAmount;

    private String status;
    private String method;
}