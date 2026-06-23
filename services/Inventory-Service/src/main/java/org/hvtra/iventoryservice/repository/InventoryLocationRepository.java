package org.hvtra.iventoryservice.repository;

import org.hvtra.iventoryservice.entity.InventoryLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface InventoryLocationRepository
        extends JpaRepository<InventoryLocation, UUID>, JpaSpecificationExecutor<InventoryLocation> {
    boolean existsByBranchIdAndCode(UUID branchId, String code);
    boolean existsByBranchIdAndCodeAndIdNot(UUID branchId, String code, UUID id);
}
