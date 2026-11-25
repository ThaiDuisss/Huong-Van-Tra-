package org.hvtra.catalog.outbox.config;

import org.hvtra.catalog.outbox.entity.CatalogOutboxEvent;
import org.hvtra.catalog.outbox.factory.CatalogOutboxEventFactory;
import org.hvtra.catalog.outbox.polling.CatalogItemSyncProperties;
import org.hvtra.catalog.outbox.polling.CatalogItemSyncPublicationStore;
import org.hvtra.catalog.outbox.polling.TransactionalCatalogOutboxEventService;
import org.hvtra.catalog.outbox.repository.CatalogOutboxEventRepository;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatalogItemSyncProperties.class)
public class CatalogOutboxConfig {

    @Bean
    public OutboxEventService<CatalogOutboxEvent> catalogOutboxEventService(
            CatalogOutboxEventRepository catalogOutboxEventRepository,
            CatalogOutboxEventFactory catalogOutboxEventFactory,
            CatalogItemSyncPublicationStore publications,
            CatalogItemSyncProperties settings
    ) {
        return new TransactionalCatalogOutboxEventService(catalogOutboxEventRepository, catalogOutboxEventFactory,
                publications, settings);
    }
}

