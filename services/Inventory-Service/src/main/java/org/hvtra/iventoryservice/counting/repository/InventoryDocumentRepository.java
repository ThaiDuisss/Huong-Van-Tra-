package org.hvtra.iventoryservice.counting.repository;

import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface InventoryDocumentRepository
        extends JpaRepository<InventoryDocument, UUID>,
                JpaSpecificationExecutor<InventoryDocument> {

    Optional<InventoryDocument> findByIdAndIsDeletedFalse(UUID id);
}
