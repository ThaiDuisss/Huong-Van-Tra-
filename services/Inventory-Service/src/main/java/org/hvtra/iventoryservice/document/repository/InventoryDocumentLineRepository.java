package org.hvtra.iventoryservice.document.repository;

import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentLineRow;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryDocumentLineRepository extends JpaRepository<InventoryDocumentLine, UUID> {
    @Query(value = """
            SELECT
                BIN_TO_UUID(l.id) AS id,
                BIN_TO_UUID(l.document_id) AS documentId,
                l.line_no AS lineNo,
                BIN_TO_UUID(l.inventory_item_id) AS inventoryItemId,
                i.reference_code AS itemSku,
                i.name AS itemName,
                i.item_type AS itemType,
                BIN_TO_UUID(l.unit_id) AS unitId,
                l.ordered_quantity AS orderedQuantity,
                l.received_quantity AS receivedQuantity,
                l.shortage_quantity AS shortageQuantity,
                l.excess_quantity AS excessQuantity,
                l.accepted_quantity AS acceptedQuantity,
                l.rejected_quantity AS rejectedQuantity,
                l.note AS note
            FROM inventory_document_lines l
            LEFT JOIN inventory_items i ON i.id = l.inventory_item_id
            WHERE l.document_id IN (:documentIds)
            ORDER BY l.document_id, l.line_no
            """, nativeQuery = true)
    List<InventoryDocumentLineRow> findLineRowsByDocumentIds(@Param("documentIds") Collection<UUID> documentIds);
}
