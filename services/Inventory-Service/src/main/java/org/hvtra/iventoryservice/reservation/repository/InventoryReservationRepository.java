package org.hvtra.iventoryservice.reservation.repository;

import org.hvtra.iventoryservice.domain.entity.InventoryReservation;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationType;
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

    List<InventoryReservation> findByOrderIdAndReservationTypeAndStatus(
            UUID orderId,
            ReservationType reservationType,
            ReservationStatus status);

    List<InventoryReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant expiresAt);

    /** Nguyên liệu đang giữ chỗ cho việc sản xuất bù một mặt hàng (sinh ra khi bán), cũ nhất trước. */
    List<InventoryReservation> findByProductVariantIdAndInventoryItem_IdAndReservationTypeAndStatusOrderByCreatedAtAsc(
            UUID productVariantId,
            UUID inventoryItemId,
            ReservationType reservationType,
            ReservationStatus status);
}
