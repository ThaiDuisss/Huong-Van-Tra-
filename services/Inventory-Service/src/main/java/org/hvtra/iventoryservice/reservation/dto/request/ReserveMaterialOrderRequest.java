package org.hvtra.iventoryservice.reservation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReserveMaterialOrderRequest(
        @NotNull UUID orderId,
        UUID branchId,
        int processingAttempt,
        Instant expiresAt,
        @NotEmpty List<@Valid ReserveMaterialLineRequest> lines
) {
    public ReserveMaterialOrderRequest(UUID orderId, Instant expiresAt, List<ReserveMaterialLineRequest> lines) {
        this(orderId, null, 0, expiresAt, lines);
    }
}
