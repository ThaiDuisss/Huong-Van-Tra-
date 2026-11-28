package org.hvtra.iventoryservice.outbox.config;

import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.iventoryservice.outbox.entity.InventoryOutboxEvent;
import org.hvtra.iventoryservice.outbox.factory.InventoryOutboxEventFactory;
import org.hvtra.iventoryservice.outbox.polling.InventoryOutboxPollingProperties;
import org.hvtra.iventoryservice.outbox.polling.InventoryOutboxPublicationStore;
import org.hvtra.iventoryservice.outbox.polling.TransactionalInventoryOutboxEventService;
import org.hvtra.iventoryservice.outbox.repository.InventoryOutboxEventRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InventoryOutboxPollingProperties.class)
public class InventoryOutboxConfig {

    @Bean
    public OutboxEventService<InventoryOutboxEvent> inventoryOutboxEventService(
            InventoryOutboxEventRepository inventoryOutboxEventRepository,
            InventoryOutboxEventFactory inventoryOutboxEventFactory,
            InventoryOutboxPublicationStore publications,
            InventoryOutboxPollingProperties settings
    ) {
        return new TransactionalInventoryOutboxEventService(
                inventoryOutboxEventRepository, inventoryOutboxEventFactory, publications, settings);
    }
}
