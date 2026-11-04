package org.hvtra.orderservice.integration.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.orderservice.integration.inventory.consumer.ReturnInventoryDispositionAckEventParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OrderEventPayloadContractTest {
    // Match Spring Boot's ObjectMapper: event subtypes may carry fields owned by the opposite ACK type.
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final DebeziumOutboxEventPayloadParser common = new DebeziumOutboxEventPayloadParser(mapper);
    private final PaymentEventPayloadParser payment = new PaymentEventPayloadParser(common);
    private final ReturnInventoryDispositionAckEventParser ack = new ReturnInventoryDispositionAckEventParser(common);

    @ParameterizedTest
    @ValueSource(strings = {"c", "r"})
    void paymentCreateOrSnapshotHydratesMissingIdentityFromOutboxRow(String op) {
        String raw = """
                {"op":"%s","after":{"event_id":"event-1","event_type":"PAYMENT_SUCCEEDED",
                  "aggregate_id":"payment-1","saga_id":"saga-1","business_key":"key-1",
                  "payload":{"orderId":"order-1","sagaId":" ","paymentId":""}}}
                """.formatted(op);
        var event = payment.parse(raw);
        assertEquals("event-1", event.getEventId());
        assertEquals("PAYMENT_SUCCEEDED", event.getEventType());
        assertEquals("key-1", event.getEventKey());
        assertEquals("payment-1", event.getPaymentId());
        assertEquals("saga-1", event.getSagaId());
        assertEquals("order-1", event.getOrderId());
    }

    @Test
    void explicitPaymentIdentityWinsOverOutboxMetadataAndSchemaWrapperIsSupported() {
        String raw = """
                {"schema":{},"payload":{"op":"c","after":{"aggregate_id":"other-payment","saga_id":"other-saga",
                  "payload":{"eventId":"explicit","eventType":"PAYMENT_FAILED","paymentId":"payment-2",
                  "sagaId":"saga-2","orderId":"order-2"}}}}
                """;
        var event = payment.parse(raw);
        assertEquals("payment-2", event.getPaymentId());
        assertEquals("saga-2", event.getSagaId());
        assertEquals("PAYMENT_FAILED", event.getEventType());
        assertEquals("explicit", event.getEventId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "{\"op\":\"u\",\"after\":{}}", "{\"op\":\"d\",\"after\":null}", "{\"op\":\"c\",\"after\":null}"})
    void nonCreateOrTombstoneIsSkippedWithoutManufacturingBusinessEvent(String raw) {
        assertTrue(payment.parseCreateEvent(raw).isEmpty());
        assertTrue(ack.parseProcessedCreateEvent(raw).isEmpty());
        assertTrue(ack.parseFailedCreateEvent(raw).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> payment.parse(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "[]", "{\"eventId\":{\"wrong\":\"shape\"}}"})
    void malformedPayloadIsRejectedRatherThanAcknowledgedAsValid(String raw) {
        assertThrows(IllegalArgumentException.class, () -> payment.parse(raw));
        assertThrows(IllegalArgumentException.class, () -> ack.parseProcessedCreateEvent(raw));
    }

    @Test
    void failedAckCannotBeParsedAsProcessedOrLoseCommandLink() {
        String raw = """
                {"op":"c","after":{"event_id":"failed-event","event_type":"RETURN_INVENTORY_DISPOSITION_FAILED",
                 "aggregate_id":"command-1","payload":{"returnId":"return-1","failureCode":"INVALID_QC"}}}
                """;
        assertTrue(ack.parseProcessedCreateEvent(raw).isEmpty());
        var failed = ack.parseFailedCreateEvent(raw).orElseThrow();
        assertEquals("command-1", failed.getCommandId());
        assertEquals("failed-event", failed.getEventId());
        assertEquals("return-1", failed.getReturnId());
        assertEquals("INVALID_QC", failed.getFailureCode());
    }

    @Test
    void processedAckKeepsExplicitCommandIdentityAndCannotBecomeFailure() {
        String raw = """
                {"op":"c","after":{"event_id":"metadata-event","event_type":"RETURN_INVENTORY_DISPOSITION_PROCESSED",
                 "aggregate_id":"other-command","payload":{"commandId":"command-2","eventId":"event-2","inspectionId":"inspection-2"}}}
                """;
        var processed = ack.parseProcessedCreateEvent(raw).orElseThrow();
        assertEquals("command-2", processed.getCommandId());
        assertEquals("event-2", processed.getEventId());
        assertEquals("inspection-2", processed.getInspectionId());
        assertTrue(ack.parseFailedCreateEvent(raw).isEmpty());
    }
}
