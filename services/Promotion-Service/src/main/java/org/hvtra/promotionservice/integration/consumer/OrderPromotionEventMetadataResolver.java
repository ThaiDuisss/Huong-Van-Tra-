package org.hvtra.promotionservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.kafka.event.EventMetadataResolver;
import org.hvtra.promotionservice.dto.event.OrderPromotionEventPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPromotionEventMetadataResolver {

    private static final String UNKNOWN_ORDER_EVENT = "UNKNOWN_ORDER_EVENT";

    private final EventMetadataResolver resolver;

    public void normalizeBeforeEventId(ConsumerRecord<String, String> record, OrderPromotionEventPayload event) {
        resolver.normalizeBeforeEventId(record, event, this::eventTypeFromTopic, UNKNOWN_ORDER_EVENT, "order promotion event");
    }

    public void normalizeEventKey(ConsumerRecord<String, String> record, OrderPromotionEventPayload event) {
        resolver.normalizeEventKey(record, event);
    }

    public String resolveEventId(ConsumerRecord<String, String> record, OrderPromotionEventPayload event) {
        return resolver.resolveEventId(record, event, "Order promotion event");
    }

    public String sourceService(ConsumerRecord<String, String> record) {
        return resolver.sourceService(record, "order-service");
    }

    public String correlationId(ConsumerRecord<String, String> record, OrderPromotionEventPayload event) {
        return resolver.correlationId(record, event.getOrderId());
    }

    public String serializeHeaders(ConsumerRecord<String, String> record) {
        return resolver.serializeHeaders(record);
    }

    private String eventTypeFromTopic(String topic) {
        if ("promotion.reservation-confirm.requested".equals(topic)) {
            return "PROMOTION_RESERVATION_CONFIRM_REQUESTED";
        }
        return null;
    }
}
