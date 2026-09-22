package org.hvtra.paymentservice.outbox.repository;


import org.hvtra.commonservice.common_core.kafka.outbox.repository.BaseOutboxEventRepository;
import org.hvtra.paymentservice.outbox.entity.OutboxEvent;

public interface OutboxEventRepository
        extends BaseOutboxEventRepository<OutboxEvent> {
}