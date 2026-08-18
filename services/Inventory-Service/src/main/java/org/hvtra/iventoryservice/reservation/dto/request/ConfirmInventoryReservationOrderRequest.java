package org.hvtra.iventoryservice.reservation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmInventoryReservationOrderRequest(@NotNull UUID orderId) {}
