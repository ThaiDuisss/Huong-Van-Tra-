package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByIdAndIsDeletedFalse(UUID id);
    boolean existsByOrderCode(String orderCode);
    List<Order> findByStatusAndExpiresAtBeforeAndIsDeletedFalse(OrderStatus status, Instant expiresAt);
}
