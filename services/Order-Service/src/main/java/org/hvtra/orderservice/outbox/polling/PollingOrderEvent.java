package org.hvtra.orderservice.outbox.polling;

import org.hvtra.orderservice.outbox.event.OrderEventType;

import java.time.Instant;

public record PollingOrderEvent(
        String outboxId,
        String claimToken,
        int attempts,
        String eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        String aggregateId,
        String key,
        String businessKey,
        String topic,
        String source,
        String payload,
        Instant occurredAt,
        String sagaId,
        String correlationId,
        String causationId
) {
    public static boolean supports(String eventType, String topic) {
        return (OrderEventType.ORDER_COMPLETED.equals(eventType) && "order.completed".equals(topic))
                || (OrderEventType.RETURN_COMPLETED.equals(eventType) && "notification.return.completed".equals(topic))
                || supportsRefundSideEffect(eventType, topic)
                || (OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED.equals(eventType)
                        && "order.event.RETURN_INVENTORY_DISPOSITION_REQUESTED".equals(topic));
    }

    static boolean supportsRefundSideEffect(String eventType, String topic) {
        return (OrderEventType.RETURN_REFUND_COMPLETED.equals(eventType)
                && "notification.return.refund-completed".equals(topic))
                || (OrderEventType.ORDER_REFUNDED.equals(eventType) && "customer.order-refunded".equals(topic));
    }
}
