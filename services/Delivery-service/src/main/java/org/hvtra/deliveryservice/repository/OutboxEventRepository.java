package org.hvtra.deliveryservice.repository;

import org.hvtra.commonservice.common_core.kafka.outbox.repository.BaseOutboxEventRepository;

import org.hvtra.deliveryservice.model.entity.OutboxEvent;

public interface OutboxEventRepository extends BaseOutboxEventRepository<OutboxEvent> {
    boolean existsByEventTypeAndBusinessKey(String eventType, String businessKey);
}
