package org.hvtra.iventoryservice.outbox.config;

import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.iventoryservice.outbox.entity.InventoryOutboxEvent;
import org.hvtra.iventoryservice.outbox.factory.InventoryOutboxEventFactory;
import org.hvtra.iventoryservice.outbox.repository.InventoryOutboxEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryOutboxConfig {

    @Bean
    public OutboxEventService<InventoryOutboxEvent> inventoryOutboxEventService(
            InventoryOutboxEventRepository inventoryOutboxEventRepository,
            InventoryOutboxEventFactory inventoryOutboxEventFactory
    ) {
        return new OutboxEventService<>(inventoryOutboxEventRepository, inventoryOutboxEventFactory);
    }
}
