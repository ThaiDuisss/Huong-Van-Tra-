package org.hvtra.deliveryservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.hvtra.deliveryservice.model.entity.DeliveryRoute;
import org.hvtra.deliveryservice.model.enums.DeliveryRouteStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {
    Optional<DeliveryRoute> findByTenantIdAndRouteCode(UUID tenantId, String routeCode);
    List<DeliveryRoute> findAllByTenantIdAndStatus(UUID tenantId, DeliveryRouteStatus status);
    boolean existsByCourierEmployeeIdAndStatusIn(UUID courierEmployeeId, List<DeliveryRouteStatus> statuses);
}
