package org.hvtra.iventoryservice.reservation.repository;

import org.hvtra.iventoryservice.domain.entity.InventoryReservation;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("""
            select r from InventoryReservation r
            where r.productVariantId = :productVariantId
              and r.inventoryItemIngredient.id = :inventoryItemId
              and r.reservationType = :reservationType
              and r.status = :status
            order by r.createdAt asc
            """)
    List<InventoryReservation> findByProductVariantIdAndInventoryItem_IdAndReservationTypeAndStatusOrderByCreatedAtAsc(
            @Param("productVariantId") UUID productVariantId,
            @Param("inventoryItemId") UUID inventoryItemId,
            @Param("reservationType") ReservationType reservationType,
            @Param("status") ReservationStatus status);
}
