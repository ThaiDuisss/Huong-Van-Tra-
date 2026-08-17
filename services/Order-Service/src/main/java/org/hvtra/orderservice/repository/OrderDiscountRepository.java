package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, UUID> {
}
