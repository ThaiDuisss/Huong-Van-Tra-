package org.hvtra.iventoryservice.counting.repository;

import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InventoryCountRepository
        extends JpaRepository<InventoryCount, UUID>,
                JpaSpecificationExecutor<InventoryCount> {

    Optional<InventoryCount> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByCountCode(String countCode);

    List<InventoryCount> findByBranchIdAndStatusInAndIsDeletedFalse(
            UUID branchId, Collection<InventoryCountStatus> statuses);
}
