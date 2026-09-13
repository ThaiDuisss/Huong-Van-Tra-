package org.hvtra.iventoryservice.counting.repository;

import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryCountLineRepository
        extends JpaRepository<InventoryCountLine, UUID> {

    List<InventoryCountLine> findByInventoryCountId(UUID countId);
}
