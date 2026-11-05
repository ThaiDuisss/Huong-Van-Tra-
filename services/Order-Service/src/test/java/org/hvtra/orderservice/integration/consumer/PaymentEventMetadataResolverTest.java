package org.hvtra.orderservice.integration.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PaymentEventMetadataResolverTest {
    private final PaymentEventMetadataResolver resolver = new PaymentEventMetadataResolver(new EventMetadataResolver(new ObjectMapper()));

    @ParameterizedTest
    @CsvSource({"payment.waiting-payment,PAYMENT_WAITING_PAYMENT", "PAYMENT_WAITING,PAYMENT_WAITING_PAYMENT",
            "PAYMENT_WAITING_PAYMENT,PAYMENT_WAITING_PAYMENT", "payment.succeeded,PAYMENT_SUCCEEDED",
            "PAYMENT_SUCCEEDED,PAYMENT_SUCCEEDED", "payment.failed,PAYMENT_FAILED", "PAYMENT_FAILED,PAYMENT_FAILED",
            "payment.expired,PAYMENT_EXPIRED", "PAYMENT_EXPIRED,PAYMENT_EXPIRED", "payment.cancelled,PAYMENT_CANCELLED",
            "PAYMENT_CANCELLED,PAYMENT_CANCELLED", "payment.need-review,PAYMENT_NEED_REVIEW", "PAYMENT_NEED_REVIEW,PAYMENT_NEED_REVIEW",
            "PAYMENT_CUSTOM,PAYMENT_CUSTOM", "unrelated,UNKNOWN_PAYMENT_EVENT"})
    void topicSuffixSelectsExactPaymentEventType(String topic, String type) {
        var event = new PaymentOrderEventPayload();
        resolver.normalizeBeforeEventId(record(topic), event);
        assertEquals(type, event.getEventType());
    }

    @Test
    void headersFillMissingIdentityWhileExplicitSagaAndPayloadTypeArePreserved() throws Exception {
        var record = record("payment.succeeded");
        record.headers().add("saga_id", "header-saga".getBytes(StandardCharsets.UTF_8));
        record.headers().add("event_id", "header-event".getBytes(StandardCharsets.UTF_8));
        record.headers().add("source_service", "payment-source".getBytes(StandardCharsets.UTF_8));
        var event = new PaymentOrderEventPayload();
        event.setEventType("PAYMENT_FAILED");
        resolver.normalizeBeforeEventId(record, event);
        resolver.normalizeEventKey(record, event);
        resolver.normalizeSagaId(record, event);
        assertEquals("PAYMENT_FAILED", event.getEventType());
        assertEquals("header-event", event.getEventId());
        assertEquals("header-saga", event.getSagaId());
        assertEquals("business-key", event.getEventKey());
        assertEquals("header-event", resolver.resolveEventId(record, event));
        assertEquals("payment-source", resolver.sourceService(record));
        assertNull(resolver.correlationId(record));
        assertEquals("header-event", new ObjectMapper().readTree(resolver.serializeHeaders(record)).get("event_id").asText());
        event.setSagaId("explicit-saga");
        resolver.normalizeSagaId(record, event);
        assertEquals("explicit-saga", event.getSagaId());
    }

    private ConsumerRecord<String, String> record(String topic) {
        return new ConsumerRecord<>(topic, 0, 1L, "business-key", "{}");
    }
}
