package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReleaseInventoryReservationOrderRequest(@NotNull UUID orderId) {}
