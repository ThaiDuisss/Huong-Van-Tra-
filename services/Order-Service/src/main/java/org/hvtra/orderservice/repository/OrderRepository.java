package org.hvtra.orderservice.domain.repository;

import org.hvtra.orderservice.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdAndIsDeletedFalse(UUID id);
    boolean existsByOrderCode(String orderCode);
}