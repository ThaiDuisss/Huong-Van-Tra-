package org.hvtra.orderservice.repository;

import org.hvtra.commonservice.common_core.kafka.inbox.repository.BaseInboxRepository;
import org.hvtra.orderservice.domain.entity.OrderInbox;

public interface OrderInboxRepository extends BaseInboxRepository<OrderInbox> {
}
