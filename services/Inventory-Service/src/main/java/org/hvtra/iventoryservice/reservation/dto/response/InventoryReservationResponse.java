package org.hvtra.iventoryservice.reservation.dto.response;

import org.hvtra.iventoryservice.domain.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryReservationResponse(
        UUID id,
        UUID orderId,
        UUID productVariantId,
        UUID ingredientId,
        UUID inventoryItemProductId,
        UUID inventoryItemIngredientId,
        UUID locationId,
        BigDecimal reservedQuantity,
        ReservationStatus status,
        Instant expiresAt
) {
    public InventoryReservationResponse(
            UUID id,
            UUID orderId,
            UUID productVariantId,
            UUID inventoryItemId,
            UUID locationId,
            BigDecimal reservedQuantity,
            ReservationStatus status,
            Instant expiresAt) {
        this(id, orderId, productVariantId, null, inventoryItemId, null, locationId, reservedQuantity, status, expiresAt);
    }

    public UUID inventoryItemId() {
        return inventoryItemIngredientId != null ? inventoryItemIngredientId : inventoryItemProductId;
    }
}
