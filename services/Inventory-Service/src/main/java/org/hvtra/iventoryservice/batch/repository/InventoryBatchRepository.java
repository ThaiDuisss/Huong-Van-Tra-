package org.hvtra.iventoryservice.batch.repository;

import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryBatchRepository
        extends JpaRepository<InventoryBatch, UUID>, JpaSpecificationExecutor<InventoryBatch> {

    @Override
    @EntityGraph(attributePaths = {"inventoryItem"})
    List<InventoryBatch> findAll();

    Optional<InventoryBatch> findByInventoryItemIdAndBatchCode(UUID inventoryItemId, String batchCode);
    List<InventoryBatch> findByInventoryItemId(UUID inventoryItemId);
    Optional<InventoryBatch> findFirstByInventoryItemIdAndStatus(UUID inventoryItemId, InventoryBatchStatus status);
    Optional<InventoryBatch> findFirstByInventoryItemId(UUID inventoryItemId);

    @Query("""
            SELECT b FROM InventoryBatch b
            WHERE b.inventoryItem.id = :itemId AND b.status = :status
            ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END ASC, b.expiryDate ASC, b.receivedAt ASC
            """)
    List<InventoryBatch> findFefoCandidates(
            @Param("itemId") UUID itemId, @Param("status") InventoryBatchStatus status);
}
