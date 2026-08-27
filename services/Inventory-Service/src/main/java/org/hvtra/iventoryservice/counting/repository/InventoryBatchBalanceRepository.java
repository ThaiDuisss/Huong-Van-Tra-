package org.hvtra.iventoryservice.counting.repository;

import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatchBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InventoryBatchBalanceRepository
        extends JpaRepository<InventoryBatchBalance, UUID>,
                JpaSpecificationExecutor<InventoryBatchBalance> {

    @Query("""
            select bb from InventoryBatchBalance bb
            join fetch bb.inventoryBatch b
            join fetch b.inventoryItem i
            join fetch bb.location l
            where l.branchId = :branchId
            and bb.quantityOnHand > 0
            """)
    List<InventoryBatchBalance> findByBranchWithPositiveBalance(
            @Param("branchId") UUID branchId);

    @Query("""
            select bb from InventoryBatchBalance bb
            join fetch bb.inventoryBatch b
            join fetch b.inventoryItem i
            join fetch bb.location l
            where l.id in :locationIds
            and bb.quantityOnHand > 0
            """)
    List<InventoryBatchBalance> findByLocationIdsWithPositiveBalance(
            @Param("locationIds") Collection<UUID> locationIds);

    @Query("""
            select bb from InventoryBatchBalance bb
            join fetch bb.inventoryBatch b
            join fetch b.inventoryItem i
            join fetch bb.location l
            where i.id in :inventoryItemIds
            and l.branchId = :branchId
            and bb.quantityOnHand > 0
            """)
    List<InventoryBatchBalance> findByItemIdsAndBranchWithPositiveBalance(
            @Param("inventoryItemIds") Collection<UUID> inventoryItemIds,
            @Param("branchId") UUID branchId);

    @Query("""
            select bb from InventoryBatchBalance bb
            join fetch bb.inventoryBatch b
            join fetch b.inventoryItem i
            join fetch bb.location l
            where b.id in :batchIds
            and l.branchId = :branchId
            and bb.quantityOnHand > 0
            """)
    List<InventoryBatchBalance> findByBatchIdsAndBranchWithPositiveBalance(
            @Param("batchIds") Collection<UUID> batchIds,
            @Param("branchId") UUID branchId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bb from InventoryBatchBalance bb
            where bb.inventoryBatch.id = :batchId and bb.location.id = :locationId
            """)
    java.util.Optional<InventoryBatchBalance> findForUpdate(
            @Param("batchId") UUID batchId,
            @Param("locationId") UUID locationId);
}
