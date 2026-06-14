package org.hvtra.orderservice.domain.repository;

import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
}