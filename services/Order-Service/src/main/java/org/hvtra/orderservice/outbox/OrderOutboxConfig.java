package org.hvtra.orderservice.outbox;

import org.hvtra.commonservice.kafka.outbox.service.OutboxEventService;
import org.hvtra.orderservice.domain.entity.OrderOutboxEvent;
import org.hvtra.orderservice.outbox.factory.OrderOutboxEventFactory;
import org.hvtra.orderservice.repository.OrderOutboxEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderOutboxConfig {

    @Bean
    public OutboxEventService<OrderOutboxEvent> orderOutboxEventService(
            OrderOutboxEventRepository orderOutboxEventRepository,
            OrderOutboxEventFactory orderOutboxEventFactory
    ) {
        return new OutboxEventService<>(orderOutboxEventRepository, orderOutboxEventFactory);
    }
}
