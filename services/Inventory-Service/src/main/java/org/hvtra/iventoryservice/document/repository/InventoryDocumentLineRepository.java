package org.hvtra.iventoryservice.document.repository;

import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryDocumentLineRepository extends JpaRepository<InventoryDocumentLine, UUID> {
    List<InventoryDocumentLine> findByDocumentId(UUID documentId);
}
