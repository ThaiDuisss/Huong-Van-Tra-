package org.hvtra.iventoryservice.document.repository;

import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentBatchAllocationRow;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentBatchAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryDocumentBatchAllocationRepository extends JpaRepository<InventoryDocumentBatchAllocation, UUID> {
    @Query(value = """
            SELECT
                BIN_TO_UUID(a.id) AS id,
                BIN_TO_UUID(a.document_line_id) AS documentLineId,
                BIN_TO_UUID(a.inventory_batch_id) AS inventoryBatchId,
                b.batch_code AS batchCode,
                b.supplier_batch_code AS supplierBatchCode,
                b.manufacture_date AS manufactureDate,
                b.expiry_date AS expiryDate,
                a.quantity AS quantity
            FROM inventory_document_batch_allocations a
            LEFT JOIN inventory_batches b ON b.id = a.inventory_batch_id
            WHERE a.document_line_id IN (:lineIds)
            """, nativeQuery = true)
    List<InventoryDocumentBatchAllocationRow> findBatchAllocationRowsByLineIds(@Param("lineIds") Collection<UUID> lineIds);
}
