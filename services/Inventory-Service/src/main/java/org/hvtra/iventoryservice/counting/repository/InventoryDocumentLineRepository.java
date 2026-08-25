package org.hvtra.iventoryservice.counting.repository;

import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryDocumentLineRepository
        extends JpaRepository<InventoryDocumentLine, UUID> {
}
