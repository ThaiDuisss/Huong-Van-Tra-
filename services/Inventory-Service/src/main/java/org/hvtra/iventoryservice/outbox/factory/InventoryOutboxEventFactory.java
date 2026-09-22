package org.hvtra.iventoryservice.outbox.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.kafka.outbox.factory.OutboxEventFactory;
import org.hvtra.iventoryservice.outbox.entity.InventoryOutboxEvent;
import org.springframework.stereotype.Component;

@Component
public class InventoryOutboxEventFactory extends OutboxEventFactory<InventoryOutboxEvent> {

    public InventoryOutboxEventFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected InventoryOutboxEvent newEvent() {
        return new InventoryOutboxEvent();
    }
}
