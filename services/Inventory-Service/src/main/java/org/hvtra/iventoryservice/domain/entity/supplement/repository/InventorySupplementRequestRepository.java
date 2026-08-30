package org.hvtra.iventoryservice.domain.entity.supplement.repository;

import org.hvtra.iventoryservice.domain.entity.supplement.InventorySupplementRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventorySupplementRequestRepository extends JpaRepository<InventorySupplementRequest, UUID>, JpaSpecificationExecutor<InventorySupplementRequest> {
    Optional<InventorySupplementRequest> findByRequestCode(String requestCode);
    Optional<InventorySupplementRequest> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
}
