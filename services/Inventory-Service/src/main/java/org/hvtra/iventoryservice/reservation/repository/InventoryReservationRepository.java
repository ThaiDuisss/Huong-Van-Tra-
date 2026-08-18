package org.hvtra.iventoryservice.repository;

import org.hvtra.iventoryservice.domain.entity.InventoryReservation;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    Optional<InventoryReservation> findByOrderIdAndProductVariantIdAndLocationId(
            UUID orderId,
            UUID productVariantId,
            UUID locationId);

    List<InventoryReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    List<InventoryReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant expiresAt);
}
