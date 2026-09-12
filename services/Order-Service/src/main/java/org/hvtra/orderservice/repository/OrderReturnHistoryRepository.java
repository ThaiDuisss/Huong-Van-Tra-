package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderReturnHistory;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderReturnHistoryRepository extends JpaRepository<OrderReturnHistory, UUID> {
    boolean existsByOrderReturnIdAndActionAndIsDeletedFalse(
            UUID returnId,
            OrderReturnHistoryAction action
    );

    List<OrderReturnHistory> findByOrderReturnIdAndIsDeletedFalseOrderByOccurredAtAscIdAsc(UUID returnId);
}
