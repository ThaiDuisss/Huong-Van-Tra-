package org.hvtra.iventoryservice.repository;

import org.hvtra.iventoryservice.entity.InventoryLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InventoryLocationRepository
        extends JpaRepository<InventoryLocation, Long>, JpaSpecificationExecutor<InventoryLocation> {
    boolean existsByBranchIdAndCode(Long branchId, String code);
    boolean existsByBranchIdAndCodeAndIdNot(Long branchId, String code, Long id);
}
