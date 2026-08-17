package org.hvtra.iventoryservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.entity.InventoryItem;
import org.hvtra.iventoryservice.shared.enums.InventoryItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository
        extends JpaRepository<InventoryItem, UUID>, JpaSpecificationExecutor<InventoryItem> {
    boolean existsByReferenceIdAndItemType(UUID referenceId, InventoryItemType itemType);
    boolean existsByReferenceIdAndItemTypeAndIdNot(
            UUID referenceId, InventoryItemType itemType, UUID id);

    Optional<InventoryItem> findByReferenceIdAndItemType(UUID referenceId, InventoryItemType itemType);

    List<InventoryItem> findByReferenceIdInAndItemType(Collection<UUID> referenceIds, InventoryItemType itemType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") UUID id);
}
