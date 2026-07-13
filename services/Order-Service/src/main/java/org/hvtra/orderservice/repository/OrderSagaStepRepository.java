package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderSagaStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderSagaStepRepository extends JpaRepository<OrderSagaStep, UUID> {

    Optional<OrderSagaStep> findBySagaIdAndStepName(UUID sagaId, String stepName);

    List<OrderSagaStep> findBySagaIdOrderByStepOrderAsc(UUID sagaId);
}
