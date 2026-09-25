package org.hvtra.iventoryservice.document.repository;

import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentExportRow;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentListItemRow;
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

    @Query("""
            SELECT DISTINCT d FROM InventoryDocument d
            LEFT JOIN FETCH d.location
            LEFT JOIN FETCH d.lines l
            LEFT JOIN FETCH l.inventoryItem
            LEFT JOIN FETCH l.batchAllocations ba
            LEFT JOIN FETCH ba.inventoryBatch
            WHERE d.id = :id AND d.isDeleted = false
            """)
    Optional<InventoryDocument> findByIdWithDetails(UUID id);

    Optional<InventoryDocument> findByIdAndIsDeletedFalse(UUID id);

    @Query("SELECT d.status, COUNT(d) FROM InventoryDocument d WHERE d.documentType = :type AND d.isDeleted = false GROUP BY d.status")
    List<Object[]> countByStatusGroupByDocumentType(InventoryDocumentType type);

    String DOCUMENT_ROW_SELECT = """
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
            """;

    @Query(value = DOCUMENT_ROW_SELECT + " WHERE d.id = :id AND d.is_deleted = false", nativeQuery = true)
    Optional<InventoryDocumentRow> findDocumentRowById(@Param("id") UUID id);

    String DOCUMENT_FILTER_WHERE = """
            WHERE d.is_deleted = false
                AND (:documentType IS NULL OR d.document_type = :documentType)
                AND (:status IS NULL OR d.status = :status)
                AND (:locationId IS NULL OR d.location_id = :locationId)
                AND (:supplierId IS NULL OR d.supplier_id = :supplierId)
                AND (:fromDate IS NULL OR d.created_at >= :fromDate)
                AND (:toDate IS NULL OR d.created_at <= :toDate)
                AND (:queryPattern IS NULL
                     OR LOWER(d.document_code) LIKE :queryPattern
                     OR LOWER(d.reference_id) LIKE :queryPattern
                     OR LOWER(d.note) LIKE :queryPattern)
            """;

    // Danh sách phân trang: chỉ lấy các trường hiển thị, tổng nhận tính bằng subquery cho đúng các dòng của trang
    @Query(value = """
            SELECT
                BIN_TO_UUID(d.id) AS id,
                d.document_code AS documentCode,
                d.status AS status,
                BIN_TO_UUID(d.supplier_id) AS supplierId,
                BIN_TO_UUID(d.location_id) AS locationId,
                loc.name AS locationName,
                (SELECT COALESCE(SUM(l.received_quantity), 0)
                 FROM inventory_document_lines l
                 WHERE l.document_id = d.id) AS totalReceivedQuantity,
                d.received_at AS receivedAt,
                BIN_TO_UUID(d.created_by) AS createdBy
            FROM inventory_documents d
            LEFT JOIN inventory_locations loc ON loc.id = d.location_id
            """ + DOCUMENT_FILTER_WHERE + """
            ORDER BY d.created_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<InventoryDocumentListItemRow> searchDocumentListItems(
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
            """ + DOCUMENT_FILTER_WHERE, nativeQuery = true)
    long countDocument(
            @Param("documentType") String documentType,
            @Param("status") String status,
            @Param("locationId") UUID locationId,
            @Param("supplierId") UUID supplierId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("queryPattern") String queryPattern
    );

    // Xuất Excel: toàn bộ phiếu khớp bộ lọc (không phân trang), số liệu dòng gom bằng GROUP BY trong cùng 1 query
    @Query(value = """
            SELECT
                d.document_code AS documentCode,
                d.reference_id AS referenceId,
                BIN_TO_UUID(d.supplier_id) AS supplierId,
                loc.name AS locationName,
                d.status AS status,
                COUNT(l.id) AS totalLines,
                COALESCE(SUM(l.received_quantity), 0) AS totalReceivedQuantity,
                COALESCE(SUM(l.accepted_quantity), 0) AS totalAcceptedQuantity,
                COALESCE(SUM(l.rejected_quantity), 0) AS totalRejectedQuantity,
                d.received_at AS receivedAt,
                d.created_at AS createdAt,
                BIN_TO_UUID(d.created_by) AS createdBy
            FROM inventory_documents d
            LEFT JOIN inventory_locations loc ON loc.id = d.location_id
            LEFT JOIN inventory_document_lines l ON l.document_id = d.id
            """ + DOCUMENT_FILTER_WHERE + """
            GROUP BY d.id, loc.name
            ORDER BY d.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<InventoryDocumentExportRow> searchDocumentExportRows(
            @Param("documentType") String documentType,
            @Param("status") String status,
            @Param("locationId") UUID locationId,
            @Param("supplierId") UUID supplierId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("queryPattern") String queryPattern,
            @Param("limit") int limit
    );
}
