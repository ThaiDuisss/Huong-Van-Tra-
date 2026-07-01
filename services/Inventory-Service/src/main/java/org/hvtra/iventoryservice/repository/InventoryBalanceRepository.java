package org.hvtra.iventoryservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.entity.InventoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    Optional<InventoryBalance> findByInventoryItemIdAndLocationId(UUID itemId, UUID locationId);

    @Modifying
    @Query("""
            update InventoryBalance b
            set b.quantityReserved = b.quantityReserved + :quantity
            where b.inventoryItem.id = :itemId
              and b.location.id = :locationId
              and (b.quantityOnHand - b.quantityReserved) >= :quantity
            """)
    int reserveAvailable(
            @Param("itemId") UUID itemId,
            @Param("locationId") UUID locationId,
            @Param("quantity") BigDecimal quantity);

    @Modifying
    @Query("""
            update InventoryBalance b
            set b.quantityReserved = b.quantityReserved - :quantity
            where b.inventoryItem.id = :itemId
              and b.location.id = :locationId
              and b.quantityReserved >= :quantity
            """)
    int releaseReserved(
            @Param("itemId") UUID itemId,
            @Param("locationId") UUID locationId,
            @Param("quantity") BigDecimal quantity);

    @Modifying
    @Query("""
            update InventoryBalance b
            set b.quantityOnHand = b.quantityOnHand - :quantity,
                b.quantityReserved = b.quantityReserved - :quantity
            where b.inventoryItem.id = :itemId
              and b.location.id = :locationId
              and b.quantityReserved >= :quantity
              and b.quantityOnHand >= :quantity
            """)
    int confirmReserved(
            @Param("itemId") UUID itemId,
            @Param("locationId") UUID locationId,
            @Param("quantity") BigDecimal quantity);

    @Query("""
            select b from InventoryBalance b
            where b.inventoryItem.referenceId in :variantIds
              and b.inventoryItem.itemType = :itemType
              and b.location.id = :warehouseId
            """)
    java.util.List<InventoryBalance> findProductVariantBalances(
            @Param("variantIds") java.util.Collection<UUID> variantIds,
            @Param("itemType") org.hvtra.iventoryservice.enums.InventoryItemType itemType,
            @Param("warehouseId") UUID warehouseId);
}

