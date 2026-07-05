package org.hvtra.paymentservice.outbox;

import org.hvtra.commonservice.kafka.outbox.service.OutboxClaimService;
import org.hvtra.paymentservice.outbox.entity.OutboxEvent;
import org.hvtra.paymentservice.outbox.repository.OutboxEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class PaymentOutboxConfig {

    @Bean
    public OutboxClaimService<OutboxEvent> paymentOutboxClaimService(
            OutboxEventRepository outboxEventRepository
    ) {
        return new OutboxClaimService<>(outboxEventRepository);
    }
}
