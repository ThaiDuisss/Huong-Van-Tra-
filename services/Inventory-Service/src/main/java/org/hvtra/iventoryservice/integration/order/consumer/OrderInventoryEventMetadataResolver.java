package org.hvtra.iventoryservice.integration.order.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.iventoryservice.integration.order.dto.OrderInventoryEventPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderInventoryEventMetadataResolver {

    private static final String UNKNOWN_ORDER_EVENT = "UNKNOWN_ORDER_EVENT";

    private final EventMetadataResolver resolver;

    public void normalizeBeforeEventId(ConsumerRecord<String, String> record, OrderInventoryEventPayload event) {
        resolver.normalizeBeforeEventId(record, event, this::eventTypeFromTopic, UNKNOWN_ORDER_EVENT, "order inventory event");
    }

    public void normalizeEventKey(ConsumerRecord<String, String> record, OrderInventoryEventPayload event) {
        resolver.normalizeEventKey(record, event);
    }

    public String resolveEventId(ConsumerRecord<String, String> record, OrderInventoryEventPayload event) {
        return resolver.resolveEventId(record, event, "Order inventory event");
    }

    public String sourceService(ConsumerRecord<String, String> record) {
        return resolver.sourceService(record, "order-service");
    }

    public String correlationId(ConsumerRecord<String, String> record, OrderInventoryEventPayload event) {
        return resolver.correlationId(record, event.getOrderId());
    }

    public String serializeHeaders(ConsumerRecord<String, String> record) {
        return resolver.serializeHeaders(record);
    }

    private String eventTypeFromTopic(String topic) {
        if ("inventory.reservation-confirm.requested".equals(topic)) {
            return "INVENTORY_RESERVATION_CONFIRM_REQUESTED";
        }
        return null;
    }
}
