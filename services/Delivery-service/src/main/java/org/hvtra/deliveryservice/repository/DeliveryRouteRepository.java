package org.hvtra.deliveryservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.hvtra.deliveryservice.model.entity.DeliveryRoute;
import org.hvtra.deliveryservice.model.enums.DeliveryRouteStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {
    Optional<DeliveryRoute> findByTenantIdAndRouteCode(UUID tenantId, String routeCode);

    @EntityGraph(attributePaths = {"stops", "stops.shipment"})
    List<DeliveryRoute> findAllByTenantIdAndStatus(UUID tenantId, DeliveryRouteStatus status);

    @EntityGraph(attributePaths = {"stops", "stops.shipment"})
    List<DeliveryRoute> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    boolean existsByCourierEmployeeIdAndStatusIn(UUID courierEmployeeId, List<DeliveryRouteStatus> statuses);

    @Query("""
            select count(distinct r.id)
            from DeliveryRoute r join r.stops stop
            where stop.shipment.id = :shipmentId
              and r.status in :statuses
            """)
    long countByShipmentIdAndStatusIn(
            @Param("shipmentId") UUID shipmentId,
            @Param("statuses") List<DeliveryRouteStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"stops", "stops.shipment"})
    @Query("select distinct r from DeliveryRoute r where r.id = :routeId")
    Optional<DeliveryRoute> findByIdForUpdate(@Param("routeId") UUID routeId);

    @EntityGraph(attributePaths = {"stops", "stops.shipment"})
    @Query("select distinct r from DeliveryRoute r where r.id = :routeId")
    Optional<DeliveryRoute> findDetailedById(@Param("routeId") UUID routeId);
}