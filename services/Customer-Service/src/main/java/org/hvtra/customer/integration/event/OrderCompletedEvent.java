package org.hvtra.customer.integration.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderCompletedEvent(
        UUID orderId,
        String orderCode,
        UUID customerId,
        BigDecimal finalAmount,
        LocalDateTime occurredAt
) {
}
