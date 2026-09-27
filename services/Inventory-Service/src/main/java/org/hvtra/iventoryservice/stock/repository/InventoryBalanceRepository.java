package org.hvtra.iventoryservice.stock.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.stock.repository.projection.AggregatedBalanceRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Query("""
            select b from InventoryBalance b
            left join fetch b.inventoryItem i
            left join fetch b.inventoryBatch batch
            left join fetch b.location l
            where b.id = :id
            """)
    Optional<InventoryBalance> findByIdWithDetails(@Param("id") UUID id);

    /**
     * Tồn kho gộp theo (item + location) cho màn hình kho cửa hàng — gộp, lọc trạng thái tồn
     * và phân trang đều thực hiện ở DB. {@code filterRefs = false} thì bỏ qua {@code refIds}.
     */
    @Query(value = """
            SELECT BIN_TO_UUID(MIN(b.id)) AS representativeBalanceId,
                   BIN_TO_UUID(b.inventory_item_id) AS inventoryItemId,
                   BIN_TO_UUID(b.location_id) AS locationId,
                   COALESCE(SUM(b.quantity_on_hand), 0) AS quantityOnHand,
                   COALESCE(SUM(b.quantity_reserved), 0) AS quantityReserved,
                   COALESCE(SUM(b.quantity_blocked), 0) AS quantityBlocked,
                   MAX(b.updated_at) AS updatedAt
            FROM inventory_balances b
            JOIN inventory_items i ON i.id = b.inventory_item_id
            JOIN inventory_locations l ON l.id = b.location_id
            WHERE (:itemType IS NULL OR i.item_type = :itemType)
              AND (:filterRefs = FALSE OR i.reference_id IN (:refIds))
              AND (:locationId IS NULL OR l.id = :locationId OR l.parent_location_id = :locationId)
            GROUP BY b.inventory_item_id, b.location_id, i.reorder_point
            HAVING (:stockStatus IS NULL
                 OR (:stockStatus = 'LOW_STOCK'
                     AND COALESCE(SUM(b.quantity_on_hand), 0) > 0
                     AND COALESCE(SUM(b.quantity_on_hand), 0) <= COALESCE(i.reorder_point, 5))
                 OR (:stockStatus = 'OUT_OF_STOCK' AND COALESCE(SUM(b.quantity_on_hand), 0) <= 0))
            ORDER BY MAX(b.created_at) DESC, b.inventory_item_id, b.location_id
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<AggregatedBalanceRow> findAggregatedStoreBalances(
            @Param("itemType") String itemType,
            @Param("filterRefs") boolean filterRefs,
            @Param("refIds") Collection<UUID> refIds,
            @Param("locationId") UUID locationId,
            @Param("stockStatus") String stockStatus,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT 1
                FROM inventory_balances b
                JOIN inventory_items i ON i.id = b.inventory_item_id
                JOIN inventory_locations l ON l.id = b.location_id
                WHERE (:itemType IS NULL OR i.item_type = :itemType)
                  AND (:filterRefs = FALSE OR i.reference_id IN (:refIds))
                  AND (:locationId IS NULL OR l.id = :locationId OR l.parent_location_id = :locationId)
                GROUP BY b.inventory_item_id, b.location_id, i.reorder_point
                HAVING (:stockStatus IS NULL
                     OR (:stockStatus = 'LOW_STOCK'
                         AND COALESCE(SUM(b.quantity_on_hand), 0) > 0
                         AND COALESCE(SUM(b.quantity_on_hand), 0) <= COALESCE(i.reorder_point, 5))
                     OR (:stockStatus = 'OUT_OF_STOCK' AND COALESCE(SUM(b.quantity_on_hand), 0) <= 0))
            ) grouped
            """, nativeQuery = true)
    long countAggregatedStoreBalances(
            @Param("itemType") String itemType,
            @Param("filterRefs") boolean filterRefs,
            @Param("refIds") Collection<UUID> refIds,
            @Param("locationId") UUID locationId,
            @Param("stockStatus") String stockStatus);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBalance b
            join fetch b.inventoryItem item
            join fetch b.inventoryBatch batch
            join fetch b.location location
            where item.id = :inventoryItemId
              and location.id = :locationId
              and location.status = :locationStatus
              and batch.status = org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus.AVAILABLE
              and (batch.expiryDate is null or batch.expiryDate >= :today)
              and (b.quantityOnHand - b.quantityReserved - b.quantityBlocked) > 0
            order by
              case when batch.expiryDate is null then 1 else 0 end,
              batch.expiryDate asc,
              batch.receivedAt asc,
              batch.id asc
            """)
    List<InventoryBalance> findFefoAvailableForUpdate(
            @Param("inventoryItemId") UUID inventoryItemId,
            @Param("locationId") UUID locationId,
            @Param("locationStatus") InventoryStatus locationStatus,
            @Param("today") LocalDate today);

    /**
     * Toàn bộ bản ghi tồn của 1 batch, KHÔNG lọc theo kho — dùng lúc duyệt lệnh sản xuất để lấy nguyên
     * liệu theo FEFO bất kể batch đang nằm ở kho nào (nguyên liệu không quản lý theo kho sản xuất).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBalance b
            join fetch b.location
            where b.inventoryBatch.id = :batchId
            """)
    List<InventoryBalance> findForUpdateByBatchId(@Param("batchId") UUID batchId);

    @Modifying
    @Query("""
            update InventoryBalance b
            set b.quantityReserved = b.quantityReserved + :quantity
            where b.inventoryItem.id = :inventoryItemId
              and b.location.id = :locationId
              and (b.quantityOnHand - b.quantityReserved - b.quantityBlocked) >= :quantity
              and b.location.id in (
                    select l.id from InventoryLocation l
                    where l.id = :locationId
                      and l.status = :locationStatus
                      and l.type not in :excludedLocationTypes
              )
            """)
    int reserveAvailable(
            @Param("inventoryItemId") UUID inventoryItemId,
            @Param("locationId") UUID locationId,
            @Param("quantity") BigDecimal quantity,
            @Param("locationStatus") InventoryStatus locationStatus,
            @Param("excludedLocationTypes") Collection<InventoryLocationType> excludedLocationTypes);

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
              and (b.quantityOnHand - b.quantityBlocked) >= :quantity
              and b.location.id in (
                    select l.id from InventoryLocation l
                    where l.id = :locationId
                      and l.status = :locationStatus
                      and l.type not in :excludedLocationTypes
              )
            """)
    int confirmReserved(
            @Param("inventoryItemId") UUID inventoryItemId,
            @Param("locationId") UUID locationId,
            @Param("quantity") BigDecimal quantity,
            @Param("locationStatus") InventoryStatus locationStatus,
            @Param("excludedLocationTypes") Collection<InventoryLocationType> excludedLocationTypes);

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
            join fetch b.inventoryItem i
            join fetch b.location l
            where i.id in :inventoryItemIds
              and l.status = :locationStatus
              and l.type not in :excludedLocationTypes
            """)
    List<InventoryBalance> findSaleableBalancesByInventoryItemIds(
            @Param("inventoryItemIds") Collection<UUID> inventoryItemIds,
            @Param("locationStatus") InventoryStatus locationStatus,
            @Param("excludedLocationTypes") Collection<InventoryLocationType> excludedLocationTypes);

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
