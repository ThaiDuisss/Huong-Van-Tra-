package org.hvtra.deliveryservice.repository;

import org.hvtra.commonservice.kafka.outbox.repository.BaseOutboxEventRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import org.hvtra.deliveryservice.model.entity.OutboxEvent;
import java.util.UUID;

public interface OutboxEventRepository extends BaseOutboxEventRepository<OutboxEvent> {
    boolean existsByEventTypeAndBusinessKey(String eventType, String businessKey);
}
