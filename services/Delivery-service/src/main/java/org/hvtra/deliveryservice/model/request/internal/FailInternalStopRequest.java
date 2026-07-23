package org.hvtra.deliveryservice.model.request.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hvtra.deliveryservice.model.enums.DeliveryAttemptStatus;

public record FailInternalStopRequest(
        @NotNull DeliveryAttemptStatus failureStatus,
        @NotBlank String failureCode,
        @NotBlank String failureReason,
        String note
) {
}
