package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
    List<OrderStatusHistory> findByOrderIdAndIsDeletedFalseOrderByChangedAtAsc(UUID orderId);

    Optional<OrderStatusHistory> findFirstByOrderIdAndToStatusAndIsDeletedFalseOrderByChangedAtDesc(
            UUID orderId,
            OrderStatus toStatus
    );
}
