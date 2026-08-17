package org.hvtra.orderservice.repository;

import org.hvtra.commonservice.kafka.outbox.repository.BaseOutboxEventRepository;
import org.hvtra.orderservice.domain.entity.OrderOutboxEvent;

public interface OrderOutboxEventRepository extends BaseOutboxEventRepository<OrderOutboxEvent> {
}
