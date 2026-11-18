package org.hvtra.customer.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.service.CustomerPurchaseService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Đơn hoàn tất: cộng chi tiêu, số đơn, tích điểm và nâng hạng cho khách. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class OrderCompletedConsumer {

    private static final String ORDER_COMPLETED = "ORDER_COMPLETED";

    private final OrderEventInboxService inboxService;
    private final CustomerPurchaseService customerPurchaseService;

    @KafkaListener(
            topics = "${customer.order-events.topics.order-completed:order.completed}",
            groupId = "${kafka-consumer-config.group-id:customer-service-order-events}"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        inboxService.handle(
                record,
                OrderCompletedEvent.class,
                ORDER_COMPLETED,
                event -> Objects.toString(event.getOrderId(), null),
                customerPurchaseService::onOrderCompleted
        );
        acknowledgment.acknowledge();
    }
}
