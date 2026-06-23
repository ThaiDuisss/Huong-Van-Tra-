package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OrderPaymentInfoResponse {
    private UUID orderId;
    private String orderCode;
    private String status;
    private String paymentStatus;
    private BigDecimal finalAmount;
    private BigDecimal paidAmount;
    private BigDecimal debtAmount;
}