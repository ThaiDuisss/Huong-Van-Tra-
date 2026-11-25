package org.hvtra.orderservice.outbox;

import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.orderservice.domain.entity.OrderOutboxEvent;
import org.hvtra.orderservice.outbox.factory.OrderOutboxEventFactory;
import org.hvtra.orderservice.outbox.polling.OrderOutboxPollingProperties;
import org.hvtra.orderservice.outbox.polling.OrderOutboxPublicationStore;
import org.hvtra.orderservice.outbox.polling.TransactionalOrderOutboxEventService;
import org.hvtra.orderservice.repository.OrderOutboxEventRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderOutboxPollingProperties.class)
public class OrderOutboxConfig {

    @Bean
    public OutboxEventService<OrderOutboxEvent> orderOutboxEventService(
            OrderOutboxEventRepository orderOutboxEventRepository,
            OrderOutboxEventFactory orderOutboxEventFactory,
            OrderOutboxPublicationStore publications,
            OrderOutboxPollingProperties settings
    ) {
        return new TransactionalOrderOutboxEventService(
                orderOutboxEventRepository, orderOutboxEventFactory, publications, settings);
    }
}
