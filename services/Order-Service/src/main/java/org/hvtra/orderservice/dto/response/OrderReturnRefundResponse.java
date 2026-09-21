package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OrderReturnRefundResponse {

    private UUID returnId;
    private UUID orderId;
    private UUID paymentId;
    private UUID refundTransactionId;
    private BigDecimal refundAmount;
    private BigDecimal totalAmountRefunded;
    private String paymentStatus;
    private ReturnStatus returnStatus;
    private PaymentStatus orderPaymentStatus;
    private OrderReturnStatus orderReturnStatus;
    private String method;
    private LocalDateTime refundedAt;
}
