package org.hvtra.paymentservice.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class OrderPaymentInfoResponse {

    private UUID orderId;
    private String orderCode;
    private String status;
    private String paymentStatus;

    private BigDecimal finalAmount;
    private BigDecimal paidAmount;
    private BigDecimal debtAmount;
}