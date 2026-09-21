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
public class PaymentRefundResponse {

    private UUID paymentId;
    private UUID orderId;
    private UUID refundTransactionId;
    private UUID returnId;
    private BigDecimal refundAmount;
    private BigDecimal totalAmountRefunded;
    private String paymentStatus;
    private String method;
    private LocalDateTime refundedAt;
}
