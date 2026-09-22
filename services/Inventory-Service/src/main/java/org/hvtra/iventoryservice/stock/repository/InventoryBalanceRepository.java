package org.hvtra.iventoryservice.stock.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBalanceRepository
        extends JpaRepository<InventoryBalance, UUID>, JpaSpecificationExecutor<InventoryBalance> {

    @Override
    @EntityGraph(attributePaths = {"inventoryItem", "location"})
    List<InventoryBalance> findAll(Specification<InventoryBalance> spec);

    @Override
    @EntityGraph(attributePaths = {"inventoryItem", "location"})
    Page<InventoryBalance> findAll(Specification<InventoryBalance> spec, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBalance b
            where b.inventoryItem.id = :itemId and b.location.id = :locationId
            """)
    Optional<InventoryBalance> findForUpdate(
            @Param("itemId") UUID itemId, @Param("locationId") UUID locationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBalance b
            where b.inventoryBatch.id = :batchId and b.location.id = :locationId
            """)
    Optional<InventoryBalance> findForUpdateByBatchId(
            @Param("batchId") UUID batchId, @Param("locationId") UUID locationId);

    @Modifying
    @Query("""
            update InventoryBalance b
            set b.quantityReserved = b.quantityReserved + :quantity
            where b.inventoryItem.id = :inventoryItemId
              and b.location.id = :locationId
              and (b.quantityOnHand - b.quantityReserved) >= :quantity
            """)
    int reserveAvailable(
            @Param("inventoryItemId") UUID inventoryItemId,
            @Param("locationId") UUID locationId,
            @Param("quantity") BigDecimal quantity);

    @Modifying
    @Query("""
            update InventoryBalance b
            set b.quantityReserved = b.quantityReserved - :quantity
            where b.inventoryItem.id = :inventoryItemId
              and b.location.id = :locationId
              and b.quantityReserved >= :quantity
            """)
    int releaseReserved(
            @Param("inventoryItemId") UUID inventoryItemId,
            @Param("locationId") UUID locationId,
            @Param("quantity") BigDecimal quantity);

    @Modifying
    @Query("""
            update InventoryBalance b
            set b.quantityOnHand = b.quantityOnHand - :quantity,
                b.quantityReserved = b.quantityReserved - :quantity
            where b.inventoryItem.id = :inventoryItemId
              and b.location.id = :locationId
              and b.quantityReserved >= :quantity
              and b.quantityOnHand >= :quantity
            """)
    int confirmReserved(
            @Param("inventoryItemId") UUID inventoryItemId,
            @Param("locationId") UUID locationId,
            @Param("quantity") BigDecimal quantity);

    @Query("""
            select b from InventoryBalance b
            join fetch b.inventoryItem i
            join fetch b.inventoryBatch batch
            join fetch b.location l
            where i.referenceId in :variantIds
              and i.itemType = :itemType
              and (:warehouseId is null or l.id = :warehouseId)
            """)
    List<InventoryBalance> findProductVariantBalances(
            @Param("variantIds") Collection<UUID> variantIds,
            @Param("itemType") InventoryItemType itemType,
            @Param("warehouseId") UUID warehouseId);

    @Query("""
            select b from InventoryBalance b
            join fetch b.inventoryItem i
            join fetch b.inventoryBatch batch
            join fetch b.location l
            where i.id in :inventoryItemIds
              and (:warehouseId is null or l.id = :warehouseId)
            """)
    List<InventoryBalance> findByInventoryItemIdsAndWarehouse(
            @Param("inventoryItemIds") Collection<UUID> inventoryItemIds,
            @Param("warehouseId") UUID warehouseId);

    @Query("""
            select b from InventoryBalance b
            join fetch b.inventoryItem i
            where i.id in :inventoryItemIds
            """)
    List<InventoryBalance> findActiveWarehouseBalancesByInventoryItemIds(
            @Param("inventoryItemIds") Collection<UUID> inventoryItemIds);

    @Query("""
            select b from InventoryBalance b
            where b.inventoryItem.id = :itemId and b.location.id = :locationId
            """)
    Optional<InventoryBalance> findByInventoryItemIdAndLocationId(
            @Param("itemId") UUID itemId, @Param("locationId") UUID locationId);

    @Query("""
            select b from InventoryBalance b
            where b.inventoryBatch.id = :batchId and b.location.id = :locationId
            """)
    Optional<InventoryBalance> findByInventoryBatchIdAndLocationId(
            @Param("batchId") UUID batchId, @Param("locationId") UUID locationId);

    @Query("""
            select b from InventoryBalance b
            join fetch b.inventoryBatch batch
            join fetch b.inventoryItem item
            join fetch b.location location
            where location.branchId = :branchId
              and b.quantityOnHand > 0
            """)
    List<InventoryBalance> findByBranchWithPositiveBalance(
            @Param("branchId") UUID branchId);

    @Query("""
            select b from InventoryBalance b
            join fetch b.inventoryBatch batch
            join fetch b.inventoryItem item
            join fetch b.location location
            where location.id in :locationIds
              and b.quantityOnHand > 0
            """)
    List<InventoryBalance> findByLocationIdsWithPositiveBalance(
            @Param("locationIds") Collection<UUID> locationIds);

    @Query("""
            select b from InventoryBalance b
            join fetch b.inventoryBatch batch
            join fetch b.inventoryItem item
            join fetch b.location location
            where item.id in :inventoryItemIds
              and location.branchId = :branchId
              and b.quantityOnHand > 0
            """)
    List<InventoryBalance> findByItemIdsAndBranchWithPositiveBalance(
            @Param("inventoryItemIds") Collection<UUID> inventoryItemIds,
            @Param("branchId") UUID branchId);

    @Query("""
            select b from InventoryBalance b
            join fetch b.inventoryBatch batch
            join fetch b.inventoryItem item
            join fetch b.location location
            where batch.id in :batchIds
              and location.branchId = :branchId
              and b.quantityOnHand > 0
            """)
    List<InventoryBalance> findByBatchIdsAndBranchWithPositiveBalance(
            @Param("batchIds") Collection<UUID> batchIds,
            @Param("branchId") UUID branchId);
}
