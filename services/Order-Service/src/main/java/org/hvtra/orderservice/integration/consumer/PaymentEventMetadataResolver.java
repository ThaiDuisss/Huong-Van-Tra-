package org.hvtra.orderservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventMetadataResolver {

    private static final String UNKNOWN_PAYMENT_EVENT = "UNKNOWN_PAYMENT_EVENT";

    private final EventMetadataResolver resolver;

    public void normalizeBeforeEventId(ConsumerRecord<String, String> record, PaymentOrderEventPayload event) {
        resolver.normalizeBeforeEventId(record, event, this::eventTypeFromTopic, UNKNOWN_PAYMENT_EVENT, "payment event");
    }

    public void normalizeEventKey(ConsumerRecord<String, String> record, PaymentOrderEventPayload event) {
        resolver.normalizeEventKey(record, event);
    }

    public void normalizeSagaId(ConsumerRecord<String, String> record, PaymentOrderEventPayload event) {
        if (event.getSagaId() == null || event.getSagaId().isBlank()) {
            event.setSagaId(resolver.headerValue(record, null, "saga_id", "sagaId"));
        }
    }

    public String resolveEventId(ConsumerRecord<String, String> record, PaymentOrderEventPayload event) {
        return resolver.resolveEventId(record, event, "Payment event");
    }

    public String sourceService(ConsumerRecord<String, String> record) {
        return resolver.sourceService(record, "payment-service");
    }

    public String correlationId(ConsumerRecord<String, String> record) {
        return resolver.correlationId(record, null);
    }

    public String serializeHeaders(ConsumerRecord<String, String> record) {
        return resolver.serializeHeaders(record);
    }

    private String eventTypeFromTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return null;
        }

        String suffix = topic.substring(topic.lastIndexOf('.') + 1);
        return switch (suffix) {
            case "payment.waiting-payment", "waiting-payment", "PAYMENT_WAITING", "PAYMENT_WAITING_PAYMENT" ->
                    "PAYMENT_WAITING_PAYMENT";
            case "payment.succeeded", "succeeded", "PAYMENT_SUCCEEDED" -> "PAYMENT_SUCCEEDED";
            case "payment.failed", "failed", "PAYMENT_FAILED" -> "PAYMENT_FAILED";
            case "payment.expired", "expired", "PAYMENT_EXPIRED" -> "PAYMENT_EXPIRED";
            case "payment.cancelled", "cancelled", "PAYMENT_CANCELLED" -> "PAYMENT_CANCELLED";
            case "payment.need-review", "need-review", "PAYMENT_NEED_REVIEW" -> "PAYMENT_NEED_REVIEW";
            default -> suffix.startsWith("PAYMENT_") ? suffix : null;
        };
    }
}
