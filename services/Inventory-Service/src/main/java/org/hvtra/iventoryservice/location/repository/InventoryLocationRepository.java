package org.hvtra.iventoryservice.location.repository;

import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLocationRepository
        extends JpaRepository<InventoryLocation, UUID>, JpaSpecificationExecutor<InventoryLocation> {
    boolean existsByCode(String code);
    boolean existsByBranchIdAndCode(UUID branchId, String code);
    boolean existsByBranchIdAndCodeAndIdNot(UUID branchId, String code, UUID id);
    Optional<InventoryLocation> findFirstByType(InventoryLocationType type);
    Optional<InventoryLocation> findFirstByTypeAndStatus(
            InventoryLocationType type,
            InventoryStatus status
    );
    Optional<InventoryLocation> findFirstByBranchIdAndTypeAndStatus(
            UUID branchId,
            InventoryLocationType type,
            InventoryStatus status
    );

    @Query("""
            SELECT DISTINCT location.branchId
            FROM InventoryLocation location
            WHERE location.status = :status
              AND location.isDeleted = false
            """)
    List<UUID> findDistinctBranchIdsByStatus(@Param("status") InventoryStatus status);

    Page<InventoryLocation> findByNameContainingIgnoreCaseAndTypeAndIsDeletedFalse(
            String name,
            InventoryLocationType type,
            Pageable pageable
    );
}
