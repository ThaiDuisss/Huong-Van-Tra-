package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.SellableAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SellableAdjustmentRepository extends JpaRepository<SellableAdjustment, UUID> {
    Optional<SellableAdjustment> findByVariantIdAndOperationKey(UUID variantId, String operationKey);
}
