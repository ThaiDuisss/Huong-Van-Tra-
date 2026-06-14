package org.hvtra.iventoryservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.entity.InventoryItem;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryItemRepository
        extends JpaRepository<InventoryItem, Long>, JpaSpecificationExecutor<InventoryItem> {
    boolean existsByReferenceIdAndItemType(Long referenceId, InventoryItemType itemType);
    boolean existsByReferenceIdAndItemTypeAndIdNot(
            Long referenceId, InventoryItemType itemType, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") Long id);
}
