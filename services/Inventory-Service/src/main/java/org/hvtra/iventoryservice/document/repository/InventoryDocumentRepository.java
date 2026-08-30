package org.hvtra.iventoryservice.document.repository;

import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
