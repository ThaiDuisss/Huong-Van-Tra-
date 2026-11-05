package org.hvtra.iventoryservice.item.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository
        extends JpaRepository<InventoryItem, UUID>, JpaSpecificationExecutor<InventoryItem> {
    List<InventoryItem> findByItemTypeAndReferenceCodeIn(InventoryItemType itemType, Collection<String> referenceCodes);

    boolean existsByReferenceIdAndItemType(UUID referenceId, InventoryItemType itemType);
    boolean existsByReferenceIdAndItemTypeAndIdNot(
            UUID referenceId, InventoryItemType itemType, UUID id);

    Optional<InventoryItem> findByReferenceIdAndItemType(UUID referenceId, InventoryItemType itemType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.referenceId = :referenceId and i.itemType = :itemType")
    Optional<InventoryItem> findByReferenceIdAndItemTypeForUpdate(
            @Param("referenceId") UUID referenceId, @Param("itemType") InventoryItemType itemType);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select i from InventoryItem i where i.referenceId = :referenceId and i.itemType = :itemType")
    Optional<InventoryItem> findByReferenceIdAndItemTypeLocked(
            @Param("referenceId") UUID referenceId,
            @Param("itemType") InventoryItemType itemType);

    Optional<InventoryItem> findByReferenceCode(String referenceCode);

    Optional<InventoryItem> findByReferenceCodeAndItemType(String referenceCode, InventoryItemType itemType);

    List<InventoryItem> findByReferenceIdInAndItemType(Collection<UUID> referenceIds, InventoryItemType itemType);

    List<InventoryItem> findByReferenceIdInAndItemTypeAndStatusOrderByNameAsc(
            Collection<UUID> referenceIds, InventoryItemType itemType, InventoryStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") UUID id);
}
