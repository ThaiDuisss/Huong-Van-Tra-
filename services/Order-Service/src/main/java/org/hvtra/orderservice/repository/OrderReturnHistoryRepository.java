package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderReturnHistory;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OrderReturnHistoryRepository extends JpaRepository<OrderReturnHistory, UUID> {
    boolean existsByOrderReturnIdAndActionAndIsDeletedFalse(
            UUID returnId,
            OrderReturnHistoryAction action
    );

    @Query("""
            select distinct h.orderReturn.id
            from OrderReturnHistory h
            where h.orderReturn.id in :returnIds
              and h.action = org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction.REFUND_COMPLETED
              and h.isDeleted = false
            """)
    Set<UUID> findCompletedRefundReturnIds(@Param("returnIds") Collection<UUID> returnIds);

    List<OrderReturnHistory> findByOrderReturnIdAndIsDeletedFalseOrderByOccurredAtAscIdAsc(UUID returnId);
}
