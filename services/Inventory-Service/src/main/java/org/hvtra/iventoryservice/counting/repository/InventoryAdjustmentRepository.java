package org.hvtra.iventoryservice.counting.repository;

import org.hvtra.iventoryservice.domain.entity.document.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Truy vấn trên {@link InventoryAdjustment} luôn tự lọc phiếu điều chỉnh (document_type ADJUSTMENT_IN / ADJUSTMENT_OUT). */
@Repository
public interface InventoryAdjustmentRepository
        extends JpaRepository<InventoryAdjustment, UUID>, JpaSpecificationExecutor<InventoryAdjustment> {

    Optional<InventoryAdjustment> findByIdAndIsDeletedFalse(UUID id);
}
