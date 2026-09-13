package org.hvtra.iventoryservice.document.repository;

import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentBatchAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryDocumentBatchAllocationRepository extends JpaRepository<InventoryDocumentBatchAllocation, UUID> {
    List<InventoryDocumentBatchAllocation> findByDocumentLineId(UUID documentLineId);
}
