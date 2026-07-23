package org.hvtra.orderservice.outbox.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.kafka.outbox.factory.OutboxEventFactory;
import org.hvtra.orderservice.domain.entity.OrderOutboxEvent;
import org.springframework.stereotype.Component;

@Component
public class OrderOutboxEventFactory extends OutboxEventFactory<OrderOutboxEvent> {

    public OrderOutboxEventFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected OrderOutboxEvent newEvent() {
        return new OrderOutboxEvent();
    }
}
