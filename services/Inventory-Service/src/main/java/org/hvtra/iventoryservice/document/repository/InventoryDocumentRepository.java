package org.hvtra.iventoryservice.document.repository;

import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentBatchAllocationRow;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentRow;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentLineRow;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryDocumentRepository extends JpaRepository<InventoryDocument, UUID>, JpaSpecificationExecutor<InventoryDocument> {

    Optional<InventoryDocument> findByDocumentCode(String documentCode);

    boolean existsByDocumentCode(String documentCode);

    @Query("SELECT COUNT(d) FROM InventoryDocument d WHERE d.documentType = :type")
    long countByDocumentType(InventoryDocumentType type);

    @Query("SELECT DISTINCT d FROM InventoryDocument d LEFT JOIN FETCH d.lines l LEFT JOIN FETCH l.batchAllocations WHERE d.id = :id")
    Optional<InventoryDocument> findByIdWithDetails(UUID id);

    Optional<InventoryDocument> findByIdAndIsDeletedFalse(UUID id);

    @Query("SELECT d.status, COUNT(d) FROM InventoryDocument d WHERE d.documentType = :type AND d.isDeleted = false GROUP BY d.status")
    List<Object[]> countByStatusGroupByDocumentType(InventoryDocumentType type);

    @Query(value = """
            SELECT
                BIN_TO_UUID(d.id) AS id,
                d.document_code AS documentCode,
                d.document_type AS documentType,
                d.status AS status,
                BIN_TO_UUID(d.branch_id) AS branchId,
                BIN_TO_UUID(d.location_id) AS locationId,
                loc.name AS locationName,
                loc.code AS locationCode,
                BIN_TO_UUID(d.supplier_id) AS supplierId,
                d.reference_type AS referenceType,
                d.reference_id AS referenceId,
                d.expected_date AS expectedDate,
                d.note AS note,
                d.created_at AS createdAt,
                d.updated_at AS updatedAt,
                BIN_TO_UUID(d.created_by) AS createdBy,
                BIN_TO_UUID(d.approved_by) AS approvedBy,
                d.approved_at AS approvedAt,
                d.approval_note AS approvalNote,
                BIN_TO_UUID(d.rejected_by) AS rejectedBy,
                d.rejected_at AS rejectedAt,
                d.rejection_reason AS rejectionReason,
                BIN_TO_UUID(d.received_by) AS receivedBy,
                d.received_at AS receivedAt,
                BIN_TO_UUID(d.qc_by) AS qcBy,
                d.qc_at AS qcAt,
                BIN_TO_UUID(d.posted_by) AS postedBy,
                d.posted_at AS postedAt
            FROM inventory_documents d
            LEFT JOIN inventory_locations loc ON loc.id = d.location_id
            WHERE (:documentType IS NULL OR d.document_type = :documentType)
                AND (:status IS NULL OR d.status = :status)
                AND (:locationId IS NULL OR d.location_id = :locationId)
                AND (:supplierId IS NULL OR d.supplier_id = :supplierId)
                AND (:fromDate IS NULL OR d.created_at >= :fromDate)
                AND (:toDate IS NULL OR d.created_at <= :toDate)
                AND (:queryPattern IS NULL
                     OR LOWER(d.document_code) LIKE :queryPattern
                     OR LOWER(d.reference_id) LIKE :queryPattern
                     OR LOWER(d.note) LIKE :queryPattern)
            ORDER BY d.created_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<InventoryDocumentRow> searchDocument(
            @Param("documentType") String documentType,
            @Param("status") String status,
            @Param("locationId") UUID locationId,
            @Param("supplierId") UUID supplierId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("queryPattern") String queryPattern,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM inventory_documents d
            WHERE (:documentType IS NULL OR d.document_type = :documentType)
                AND (:status IS NULL OR d.status = :status)
                AND (:locationId IS NULL OR d.location_id = :locationId)
                AND (:supplierId IS NULL OR d.supplier_id = :supplierId)
                AND (:fromDate IS NULL OR d.created_at >= :fromDate)
                AND (:toDate IS NULL OR d.created_at <= :toDate)
                AND (:queryPattern IS NULL
                     OR LOWER(d.document_code) LIKE :queryPattern
                     OR LOWER(d.reference_id) LIKE :queryPattern
                     OR LOWER(d.note) LIKE :queryPattern)
            """, nativeQuery = true)
    long countDocument(
            @Param("documentType") String documentType,
            @Param("status") String status,
            @Param("locationId") UUID locationId,
            @Param("supplierId") UUID supplierId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("queryPattern") String queryPattern
    );



}
