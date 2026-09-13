package org.hvtra.iventoryservice.movement.repository;

import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface InventoryTransactionLineRepository extends JpaRepository<InventoryTransactionLine, UUID> {

    @Query("""
            select coalesce(sum(line.quantity), 0)
            from InventoryTransactionLine line
            where line.inventoryItem.id = :inventoryItemId
              and line.location.id = :locationId
              and line.transaction.createdAt > :snapshotAt
            """)
    BigDecimal sumMovementAfterSnapshot(
            @Param("inventoryItemId") UUID inventoryItemId,
            @Param("locationId") UUID locationId,
            @Param("snapshotAt") Instant snapshotAt);

    @Query("""
            select coalesce(sum(allocation.quantity), 0)
            from InventoryTransactionBatchAllocation allocation
            where allocation.inventoryBatch.id = :batchId
              and allocation.transactionLine.location.id = :locationId
              and allocation.transactionLine.transaction.createdAt > :snapshotAt
            """)
    BigDecimal sumBatchMovementAfterSnapshot(
            @Param("batchId") UUID batchId,
            @Param("locationId") UUID locationId,
            @Param("snapshotAt") Instant snapshotAt);
}
