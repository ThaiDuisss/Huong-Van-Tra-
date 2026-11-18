package org.hvtra.customer.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.customer.integration.event.OrderRefundedEvent;
import org.hvtra.customer.service.CustomerPurchaseService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Đơn được hoàn tiền: trừ chi tiêu và trừ lại điểm; hạng giữ nguyên. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class OrderRefundedConsumer {

    private static final String ORDER_REFUNDED = "ORDER_REFUNDED";

    private final OrderEventInboxService inboxService;
    private final CustomerPurchaseService customerPurchaseService;

    @KafkaListener(
            topics = "${customer.order-events.topics.order-refunded:customer.order-refunded}",
            groupId = "${kafka-consumer-config.group-id:customer-service-order-events}"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        inboxService.handle(
                record,
                OrderRefundedEvent.class,
                ORDER_REFUNDED,
                event -> Objects.toString(event.getOrderId(), null),
                customerPurchaseService::onOrderRefunded
        );
        acknowledgment.acknowledge();
    }
}
