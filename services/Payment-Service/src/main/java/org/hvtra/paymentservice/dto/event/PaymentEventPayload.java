package org.hvtra.paymentservice.dto.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentEventPayload(
        UUID paymentId,
        UUID attemptId,
        UUID transactionId,
        String paymentCode,
        UUID orderId,
        String orderCode,
        UUID customerId,
        UUID sagaId,
        String method,
        String provider,
        String status,
        BigDecimal amountRequired,
        BigDecimal amountPaid,
        String currency,
        LocalDateTime paidAt,
        LocalDateTime expiresAt,
        String reason
) {
}