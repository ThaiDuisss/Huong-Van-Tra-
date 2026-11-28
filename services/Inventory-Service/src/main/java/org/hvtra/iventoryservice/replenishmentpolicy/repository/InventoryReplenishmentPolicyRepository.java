package org.hvtra.iventoryservice.replenishmentpolicy.repository;

import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentPolicy;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReplenishmentPolicyRepository
        extends JpaRepository<InventoryReplenishmentPolicy, UUID>, JpaSpecificationExecutor<InventoryReplenishmentPolicy> {

    @Override
    @EntityGraph(attributePaths = {"inventoryItem"})
    Page<InventoryReplenishmentPolicy> findAll(Specification<InventoryReplenishmentPolicy> spec, Pageable pageable);

    @Query("""
            select p from InventoryReplenishmentPolicy p
            join fetch p.inventoryItem
            where p.id = :id and p.isDeleted = false
            """)
    Optional<InventoryReplenishmentPolicy> findByIdWithDetails(@Param("id") UUID id);

    Optional<InventoryReplenishmentPolicy> findByInventoryItemIdAndLocationTypeAndIsDeletedFalse(
            UUID inventoryItemId, InventoryLocationType locationType);

    boolean existsByInventoryItemIdAndLocationTypeAndIsDeletedFalse(
            UUID inventoryItemId, InventoryLocationType locationType);

    boolean existsByInventoryItemIdAndLocationTypeAndIsDeletedFalseAndIdNot(
            UUID inventoryItemId, InventoryLocationType locationType, UUID id);

    List<InventoryReplenishmentPolicy> findByInventoryItemIdInAndIsDeletedFalse(
            Collection<UUID> inventoryItemIds);
}
