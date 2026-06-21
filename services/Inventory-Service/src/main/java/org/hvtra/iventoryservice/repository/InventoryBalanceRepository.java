package org.hvtra.iventoryservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.entity.InventoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
