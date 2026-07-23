package org.hvtra.paymentservice.outbox;

import org.hvtra.commonservice.kafka.outbox.service.OutboxEventService;
import org.hvtra.paymentservice.outbox.entity.OutboxEvent;
import org.hvtra.paymentservice.outbox.factory.PaymentOutboxEventFactory;
import org.hvtra.paymentservice.outbox.repository.OutboxEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentOutboxConfig {

    @Bean
    public OutboxEventService<OutboxEvent> paymentOutboxEventService(
            OutboxEventRepository outboxEventRepository,
            PaymentOutboxEventFactory paymentOutboxEventFactory
    ) {
        return new OutboxEventService<>(outboxEventRepository, paymentOutboxEventFactory);
    }
}
