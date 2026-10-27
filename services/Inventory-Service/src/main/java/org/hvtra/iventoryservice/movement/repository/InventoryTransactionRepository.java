package org.hvtra.iventoryservice.movement.repository;

import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends
        JpaRepository<InventoryTransaction, UUID>,
        JpaSpecificationExecutor<InventoryTransaction> {

    /**
     * Nạp transaction kèm lines cho 1 trang đã phân trang — tránh N+1 khi map lines từng transaction.
     */
    @Query("""
            select distinct t from InventoryTransaction t
            left join fetch t.lines
            where t.id in :ids
            """)
    List<InventoryTransaction> findAllWithLinesByIdIn(@Param("ids") Collection<UUID> ids);

    /** Mã giao dịch là duy nhất, dùng để chặn ghi trùng khi một đơn bán được xử lý lại. */
    boolean existsByTransactionCode(String transactionCode);
}
