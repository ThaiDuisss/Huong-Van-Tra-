package org.hvtra.iventoryservice.batch.repository;

import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryBatchRepository
        extends JpaRepository<InventoryBatch, UUID>, JpaSpecificationExecutor<InventoryBatch> {
    Optional<InventoryBatch> findByInventoryItemIdAndBatchCode(UUID inventoryItemId, String batchCode);
    List<InventoryBatch> findByInventoryItemId(UUID inventoryItemId);
    Optional<InventoryBatch> findFirstByInventoryItemIdAndStatus(UUID inventoryItemId, InventoryBatchStatus status);
    Optional<InventoryBatch> findFirstByInventoryItemId(UUID inventoryItemId);
}
