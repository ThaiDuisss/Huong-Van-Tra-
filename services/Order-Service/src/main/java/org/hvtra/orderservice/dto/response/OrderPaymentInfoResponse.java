package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OrderPaymentInfoResponse {
    private UUID orderId;
    private String orderCode;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private BigDecimal finalAmount;
    private BigDecimal paidAmount;
    private BigDecimal debtAmount;
}