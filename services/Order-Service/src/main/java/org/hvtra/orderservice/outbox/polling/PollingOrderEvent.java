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
        Instant occurredAt
) {
    public static boolean supports(String eventType, String topic) {
        return (OrderEventType.ORDER_COMPLETED.equals(eventType) && "order.completed".equals(topic))
                || (OrderEventType.RETURN_COMPLETED.equals(eventType) && "notification.return.completed".equals(topic));
    }
}
