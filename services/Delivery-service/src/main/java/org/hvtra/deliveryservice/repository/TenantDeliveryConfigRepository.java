package org.hvtra.deliveryservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.deliveryservice.model.entity.TenantDeliveryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantDeliveryConfigRepository extends JpaRepository<TenantDeliveryConfig, UUID> {
    Optional<TenantDeliveryConfig> findByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TenantDeliveryConfig c where c.tenantId = :tenantId")
    Optional<TenantDeliveryConfig> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);

}
