package org.hvtra.paymentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentRefundSummaryResponse {

    private UUID returnId;
    private UUID orderId;
    private UUID paymentId;
    private UUID refundTransactionId;
    private BigDecimal refundAmount;
    private BigDecimal totalAmountRefunded;
    private BigDecimal amountPaid;
    private String refundStatus;
    private String paymentStatus;
    private String method;
    private LocalDateTime refundedAt;
}
