package org.hvtra.orderservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.commonservice.kafka.saga.enums.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderSagaRepository extends JpaRepository<OrderSaga, UUID> {

    Optional<OrderSaga> findBySagaId(UUID sagaId);

    Optional<OrderSaga> findByOrderId(UUID orderId);

    boolean existsBySagaId(UUID sagaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSaga s where s.sagaId = :sagaId")
    Optional<OrderSaga> findBySagaIdForUpdate(@Param("sagaId") UUID sagaId);

    List<OrderSaga> findByStatusAndUpdatedAtBefore(
            SagaStatus status,
            Instant updatedAt
    );
}