package org.hvtra.notificationservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.notificationservice.service.NotificationEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class OrderNotificationEventConsumer {

    private final NotificationEventService notificationEventService;

    @KafkaListener(
            topics = {
                    "${notification.order.events.topics.payment-succeeded:order.event.ORDER_PAYMENT_SUCCEEDED}",
                    "${notification.order.events.topics.payment-failed:order.event.ORDER_PAYMENT_FAILED}",
                    "${notification.order.events.topics.confirmed:order.event.ORDER_CONFIRMED}",
                    "${notification.order.events.topics.cancelled:order.event.ORDER_CANCELLED}",
                    "${notification.order.events.topics.status-changed:order.event.ORDER_STATUS_CHANGED}",
                    "${notification.order.events.topics.completed}",
                    "${notification.return.events.topics.created:notification.return.created}",
                    "${notification.return.events.topics.received:notification.return.received}",
                    "${notification.return.events.topics.cancelled:notification.return.cancelled}",
                    "${notification.return.events.topics.refund-completed:notification.return.refund-completed}",
                    "${notification.return.events.topics.completed:notification.return.completed}"
            },
            groupId = "${spring.application.name:notification-service}-order-events"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        notificationEventService.handleOrderEvent(record);
        acknowledgment.acknowledge();
    }
}
