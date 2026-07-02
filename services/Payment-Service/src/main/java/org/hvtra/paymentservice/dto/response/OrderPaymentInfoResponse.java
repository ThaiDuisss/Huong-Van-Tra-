package org.hvtra.paymentservice.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class OrderPaymentInfoResponse {

    private UUID orderId;
    private String orderCode;
    private UUID customerId;
    private UUID sagaId;
    private BigDecimal finalAmount;
    private String paymentStatus;
}