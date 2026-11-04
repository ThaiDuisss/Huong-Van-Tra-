package org.hvtra.catalog.outbox.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.catalog.outbox.entity.CatalogOutboxEvent;
import org.hvtra.commonservice.common_core.kafka.outbox.factory.OutboxEventFactory;
import org.springframework.stereotype.Component;

@Component
public class CatalogOutboxEventFactory extends OutboxEventFactory<CatalogOutboxEvent> {

    public CatalogOutboxEventFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected CatalogOutboxEvent newEvent() {
        return new CatalogOutboxEvent();
    }
}

