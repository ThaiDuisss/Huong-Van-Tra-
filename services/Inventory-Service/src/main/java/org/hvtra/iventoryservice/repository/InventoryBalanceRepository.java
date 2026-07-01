package org.hvtra.iventoryservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.entity.InventoryBalance;
import org.hvtra.iventoryservice.enums.InventoryItemType;
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBalance b
            where b.inventoryItem.id = :itemId and b.location.id = :locationId
            """)
    Optional<InventoryBalance> findForUpdate(
            @Param("itemId") UUID itemId, @Param("locationId") UUID locationId);

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
            join fetch b.location l
            where i.referenceId in :variantIds
              and i.itemType = :itemType
              and (:warehouseId is null or l.id = :warehouseId)
            """)
    List<InventoryBalance> findProductVariantBalances(
            @Param("variantIds") Collection<UUID> variantIds,
            @Param("itemType") InventoryItemType itemType,
            @Param("warehouseId") UUID warehouseId);
}
