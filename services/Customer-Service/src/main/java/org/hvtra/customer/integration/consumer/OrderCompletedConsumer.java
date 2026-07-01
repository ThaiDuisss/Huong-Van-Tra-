package org.hvtra.customer.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.service.LoyaltyTierService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class OrderCompletedConsumer {
    private final LoyaltyTierService loyaltyTierService;

    @KafkaListener(topics = "${loyalty.tier.events.order-completed-topic:order.completed}", groupId = "${spring.application.name}")
    public void handle(OrderCompletedEvent event) {
        if (event == null || event.customerId() == null || event.orderId() == null) {
            return;
        }
        loyaltyTierService.recordPurchase(event);
    }
}
