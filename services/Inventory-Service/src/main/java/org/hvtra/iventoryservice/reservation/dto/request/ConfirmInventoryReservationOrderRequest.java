package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmInventoryReservationOrderRequest(@NotNull UUID orderId) {}
