package org.hvtra.notificationservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderNotificationEventMetadataResolver {

    private static final String UNKNOWN_ORDER_EVENT = "UNKNOWN_ORDER_EVENT";

    private final EventMetadataResolver resolver;

    public void normalizeBeforeEventId(ConsumerRecord<String, String> record, OrderNotificationEventPayload event) {
        resolver.normalizeBeforeEventId(record, event, this::eventTypeFromTopic, UNKNOWN_ORDER_EVENT, "order notification event");
    }

    public void normalizeEventKey(ConsumerRecord<String, String> record, OrderNotificationEventPayload event) {
        resolver.normalizeEventKey(record, event);
    }

    public String resolveEventId(ConsumerRecord<String, String> record, OrderNotificationEventPayload event) {
        return resolver.resolveEventId(record, event, "Order notification event");
    }

    public String sourceService(ConsumerRecord<String, String> record) {
        return resolver.sourceService(record, "order-service");
    }

    public String correlationId(ConsumerRecord<String, String> record, OrderNotificationEventPayload event) {
        return resolver.correlationId(record, event.getCorrelationId());
    }

    public String aggregateType(ConsumerRecord<String, String> record) {
        return resolver.headerValue(record, "aggregate_type", "ORDER");
    }

    public String aggregateId(ConsumerRecord<String, String> record, OrderNotificationEventPayload event) {
        return resolver.headerValue(record, event.getOrderId(), "aggregate_id", "aggregateId");
    }

    public String serializeHeaders(ConsumerRecord<String, String> record) {
        return resolver.serializeHeaders(record);
    }

    private String eventTypeFromTopic(String topic) {
        if (topic == null || !topic.startsWith("order.event.")) {
            return null;
        }
        return topic.substring("order.event.".length());
    }
}
