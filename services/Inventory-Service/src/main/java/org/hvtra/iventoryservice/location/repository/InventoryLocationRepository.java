package org.hvtra.iventoryservice.location.repository;

import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;
import java.util.Optional;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryLocationRepository
        extends JpaRepository<InventoryLocation, UUID>, JpaSpecificationExecutor<InventoryLocation> {
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

    Page<InventoryLocation> findByNameContainingIgnoreCaseAndTypeAndIsDeletedFalse(
            String name,
            InventoryLocationType type,
            Pageable pageable
    );
}
