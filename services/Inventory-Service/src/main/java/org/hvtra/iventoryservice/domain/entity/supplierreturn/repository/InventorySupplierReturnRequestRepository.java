package org.hvtra.iventoryservice.domain.entity.supplierreturn.repository;

import org.hvtra.iventoryservice.domain.entity.supplierreturn.InventorySupplierReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventorySupplierReturnRequestRepository extends JpaRepository<InventorySupplierReturnRequest, UUID>, JpaSpecificationExecutor<InventorySupplierReturnRequest> {
    Optional<InventorySupplierReturnRequest> findByRequestCode(String requestCode);
    Optional<InventorySupplierReturnRequest> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
}
