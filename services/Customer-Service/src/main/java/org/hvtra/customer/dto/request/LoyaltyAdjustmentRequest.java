package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Điều chỉnh điểm thủ công: points dương là cộng, âm là trừ, khác 0. */
public record LoyaltyAdjustmentRequest(
        @NotNull Integer points,
        @NotBlank @Size(max = 500) String reason
) {
}
