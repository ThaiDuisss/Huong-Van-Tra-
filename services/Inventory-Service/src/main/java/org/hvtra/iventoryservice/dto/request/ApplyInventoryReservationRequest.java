package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApplyInventoryReservationRequest(
        @NotNull UUID orderId,
        UUID branchId,
        @NotNull UUID productVariantId,
        UUID locationId,
        @NotNull @PositiveOrZero BigDecimal targetQuantity,
        @NotNull Instant expiresAt
) {}
