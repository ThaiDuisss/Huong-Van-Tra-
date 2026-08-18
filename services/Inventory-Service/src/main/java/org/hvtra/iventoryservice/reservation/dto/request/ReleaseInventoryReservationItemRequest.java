package org.hvtra.iventoryservice.reservation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReleaseInventoryReservationItemRequest(
        @NotNull UUID orderId,
        @NotNull UUID productVariantId,
        UUID locationId
) {}
