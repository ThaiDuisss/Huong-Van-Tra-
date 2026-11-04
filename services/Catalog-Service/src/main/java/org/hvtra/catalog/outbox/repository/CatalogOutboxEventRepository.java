package org.hvtra.catalog.outbox.repository;

import org.hvtra.catalog.outbox.entity.CatalogOutboxEvent;
import org.hvtra.commonservice.common_core.kafka.outbox.repository.BaseOutboxEventRepository;

public interface CatalogOutboxEventRepository extends BaseOutboxEventRepository<CatalogOutboxEvent> {
}

