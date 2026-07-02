package org.hvtra.paymentservice.repository;

import org.hvtra.paymentservice.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderIdAndIsDeletedFalse(UUID orderId);

    boolean existsByOrderIdAndStatusAndIsDeletedFalse(UUID orderId, String status);
}