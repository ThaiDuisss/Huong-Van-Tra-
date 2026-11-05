package org.hvtra.catalog.outbox.config;

import org.hvtra.catalog.outbox.entity.CatalogOutboxEvent;
import org.hvtra.catalog.outbox.factory.CatalogOutboxEventFactory;
import org.hvtra.catalog.outbox.repository.CatalogOutboxEventRepository;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogOutboxConfig {

    @Bean
    public OutboxEventService<CatalogOutboxEvent> catalogOutboxEventService(
            CatalogOutboxEventRepository catalogOutboxEventRepository,
            CatalogOutboxEventFactory catalogOutboxEventFactory
    ) {
        return new OutboxEventService<>(catalogOutboxEventRepository, catalogOutboxEventFactory);
    }
}

