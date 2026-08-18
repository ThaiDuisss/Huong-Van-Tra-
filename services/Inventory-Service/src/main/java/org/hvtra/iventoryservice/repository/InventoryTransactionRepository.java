package org.hvtra.iventoryservice.repository;

import org.hvtra.iventoryservice.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface InventoryTransactionRepository extends
        JpaRepository<InventoryTransaction, UUID>,
        JpaSpecificationExecutor<InventoryTransaction> {

    /**
     * Trả về danh sách ID của các transaction có ít nhất 1 line
     * tại SHELF mà parent location là WAREHOUSE đầu tiên (ACTIVE).
     */
    @Query("""
            SELECT DISTINCT t.id FROM InventoryTransaction t
            JOIN t.lines l
            JOIN l.location loc
            JOIN loc.parentLocation parent
            WHERE loc.type = InventoryLocationType.SHELF
              AND parent.type = InventoryLocationType.WAREHOUSE
              AND parent.status = InventoryStatus.ACTIVE
              AND parent.id = (
                  SELECT pl.id FROM InventoryLocation pl
                  WHERE pl.type = InventoryLocationType.WAREHOUSE
                    AND pl.status = InventoryStatus.ACTIVE
                  ORDER BY pl.createdAt ASC
                  LIMIT 1
              )
            """)
    List<UUID> findStoreTransactionIds();
}
