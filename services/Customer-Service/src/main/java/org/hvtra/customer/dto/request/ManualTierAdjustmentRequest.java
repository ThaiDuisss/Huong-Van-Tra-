package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record ManualTierAdjustmentRequest(
        @NotNull UUID tierId,
        @NotBlank String reason,
        LocalDateTime effectiveFrom
) {
}
