package org.hvtra.deliveryservice.repository;

import org.hvtra.deliveryservice.model.entity.TenantDeliveryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantDeliveryConfigRepository extends JpaRepository<TenantDeliveryConfig, UUID> {
    Optional<TenantDeliveryConfig> findByTenantId(UUID tenantId);
}
