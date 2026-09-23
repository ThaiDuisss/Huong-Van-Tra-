package org.hvtra.iventoryservice.outbox.repository;

import org.hvtra.commonservice.common_core.kafka.outbox.repository.BaseOutboxEventRepository;
import org.hvtra.iventoryservice.outbox.entity.InventoryOutboxEvent;

public interface InventoryOutboxEventRepository extends BaseOutboxEventRepository<InventoryOutboxEvent> {
}
