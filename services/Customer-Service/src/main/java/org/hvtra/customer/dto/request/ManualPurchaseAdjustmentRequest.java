package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ManualPurchaseAdjustmentRequest(
        @NotNull UUID customerId,
        @NotNull BigDecimal amount,
        Integer orderCountDelta,
        LocalDateTime occurredAt,
        @NotBlank String reason
) {
}
