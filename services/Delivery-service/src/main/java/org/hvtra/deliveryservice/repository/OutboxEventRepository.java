package org.hvtra.deliveryservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.hvtra.deliveryservice.model.entity.OutboxEvent;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    boolean existsByEventTypeAndBusinessKey(String eventType, String businessKey);
}
