package org.hvtra.paymentservice.outbox.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.kafka.outbox.factory.OutboxEventFactory;
import org.hvtra.paymentservice.outbox.entity.OutboxEvent;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutboxEventFactory extends OutboxEventFactory<OutboxEvent> {

    public PaymentOutboxEventFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected OutboxEvent newEvent() {
        return new OutboxEvent();
    }
}