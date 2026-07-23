package org.hvtra.orderservice.repository;

import org.hvtra.commonservice.kafka.inbox.repository.BaseInboxRepository;
import org.hvtra.orderservice.domain.entity.OrderInbox;

public interface OrderInboxRepository extends BaseInboxRepository<OrderInbox> {
}
