package org.hvtra.deliveryservice.repository;

import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.enums.DeliveryMode;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByTenantIdAndShipmentCode(UUID tenantId, String shipmentCode);
    List<Shipment> findAllByOrderIdOrderByShipmentNoAsc(UUID orderId);
    Optional<Shipment> findByProviderAndTrackingCode(org.hvtra.deliveryservice.model.enums.DeliveryProvider provider, String trackingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s where s.id = :shipmentId")
    Optional<Shipment> findByIdForUpdate(@Param("shipmentId") UUID shipmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Shipment s
            where s.tenantId = :tenantId
              and s.deliveryMode = :deliveryMode
              and s.provider = :provider
              and s.status = :status
            order by s.requestedTo asc nulls last, s.createdAt asc
            """)
    List<Shipment> lockPlanningCandidates(
            @Param("tenantId") UUID tenantId,
            @Param("deliveryMode") DeliveryMode deliveryMode,
            @Param("provider") DeliveryProvider provider,
            @Param("status") ShipmentStatus status,
            Pageable pageable
    );
}
