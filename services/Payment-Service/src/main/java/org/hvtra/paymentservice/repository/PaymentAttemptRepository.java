package org.hvtra.paymentservice.repository;

import org.hvtra.paymentservice.domain.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findTopByPaymentIdOrderByAttemptNoDesc(UUID paymentId);
}