package org.hvtra.customer.integration.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderPartiallyRefundedEvent(
        UUID orderId,
        UUID refundId,
        UUID customerId,
        BigDecimal refundAmount,
        LocalDateTime refundedAt,
        String reason
) {
}
