package org.hvtra.paymentservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.paymentservice.domain.entity.Payment;
import org.hvtra.paymentservice.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    List<Payment> findByOrderIdInAndIsDeletedFalse(List<UUID> orderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment
            from Payment payment
            where payment.orderId = :orderId
              and payment.isDeleted = false
            """)
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);
}
