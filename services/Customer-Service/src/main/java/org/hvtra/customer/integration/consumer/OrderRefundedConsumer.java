package org.hvtra.customer.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.integration.event.OrderFullyRefundedEvent;
import org.hvtra.customer.integration.event.OrderPartiallyRefundedEvent;
import org.hvtra.customer.service.LoyaltyTierService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class OrderRefundedConsumer {
    private final LoyaltyTierService loyaltyTierService;

    @KafkaListener(topics = "${loyalty.tier.events.order-partially-refunded-topic:order.partially-refunded}", groupId = "${spring.application.name}")
    public void handlePartialRefund(OrderPartiallyRefundedEvent event) {
        if (event == null || event.customerId() == null || event.refundId() == null) {
            return;
        }
        loyaltyTierService.recordPartialRefund(event);
    }

    @KafkaListener(topics = "${loyalty.tier.events.order-fully-refunded-topic:order.fully-refunded}", groupId = "${spring.application.name}")
    public void handleFullRefund(OrderFullyRefundedEvent event) {
        if (event == null || event.customerId() == null || event.refundId() == null) {
            return;
        }
        loyaltyTierService.recordFullRefund(event);
    }
}
