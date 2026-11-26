package org.hvtra.orderservice.outbox.polling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.hvtra.orderservice.outbox.service.OrderKafkaSigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderOutboxPollingPublisherTest {
    private static final String TOPIC = "order.completed";
    private static final Instant OCCURRED = Instant.parse("2026-11-24T03:00:00.123456Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final OrderKafkaSigner signer = signer("1");
    private final OrderOutboxRecordFactory records = new OrderOutboxRecordFactory(mapper, signer);
    private final OrderOutboxPublicationStore publications = mock(OrderOutboxPublicationStore.class);
    @SuppressWarnings("unchecked")
    private final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
    private final OrderOutboxPollingProperties settings = new OrderOutboxPollingProperties(
            OrderOutboxPollingProperties.Mode.POLLING, true, 1000, 25, 200, 500, 1000, 120000);

    @Test
    void makesContractRecordFromPersistedBodyAndPreservesTheWireValues() throws Exception {
        PollingOrderEvent event = order("valid", signer, false);
        var record = records.create(event);
        assertEquals(event.payload(), record.value());
        assertEquals(event.key(), record.key());
        assertEquals(OCCURRED.toEpochMilli(), record.timestamp());
        assertEquals(event.eventId(), header(record, "event_id"));
        assertEquals(event.key(), header(record, "correlation_id"));
        assertEquals(event.businessKey(), header(record, "business_key"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sendsRefundEventsWithTheExistingTopicsKeysBodyAndHeaders(boolean customerRefund) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String returnId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String transactionId = UUID.randomUUID().toString();
        String eventType = customerRefund ? "ORDER_REFUNDED" : "RETURN_REFUND_COMPLETED";
        String key = customerRefund ? orderId : returnId;
        String businessKey = returnId + (customerRefund ? ":order-refunded" : ":return-refund-completed");
        String topic = customerRefund ? "customer.order-refunded" : "notification.return.refund-completed";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", eventType);
        body.put("eventKey", customerRefund ? businessKey : returnId);
        body.put("orderId", orderId);
        body.put("occurredAt", OCCURRED.toString());
        if (customerRefund) {
            body.put("refundId", returnId);
            body.put("customerId", UUID.randomUUID().toString());
            body.put("refundAmount", new BigDecimal("90000"));
            body.put("fullRefund", false);
        } else {
            body.put("returnId", returnId);
            body.put("paymentId", UUID.randomUUID().toString());
            body.put("refundTransactionId", transactionId);
            body.put("newStatus", "COMPLETED");
        }
        String payload = mapper.writeValueAsString(body);
        PollingOrderEvent event = new PollingOrderEvent("refund", "token", 1, eventId, eventType, 1,
                "ORDER_RETURN", returnId, key, businessKey, topic, "order-service", payload, OCCURRED,
                null, key, transactionId);
        var record = records.create(event);
        assertEquals(topic, record.topic());
        assertEquals(key, record.key());
        assertEquals(payload, record.value());
        assertEquals(returnId, header(record, "aggregate_id"));
        assertEquals(businessKey, header(record, "business_key"));
        assertEquals(key, header(record, "correlation_id"));
        assertEquals(transactionId, header(record, "causation_id"));
    }

    @Test
    void invalidSignatureIsQuarantinedWhileTheFollowingEventIsAcked() throws Exception {
        PollingOrderEvent invalid = order("bad", signer, true);
        PollingOrderEvent valid = order("good", signer, false);
        setup(invalid, valid);
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(call -> ack(call.getArgument(0)));
        when(publications.sent(valid)).thenReturn(true);

        try (var publisher = publisher()) {
            publisher.pollOnce();
        }
        var ordered = inOrder(publications, kafka);
        ordered.verify(publications).quarantine(invalid, "INVALID_OR_MISSING_HMAC");
        ordered.verify(kafka).send(any(ProducerRecord.class));
        ordered.verify(publications).sent(valid);
        verify(publications, never()).sent(invalid);
    }

    @Test
    void timeoutRetainsRowForRetryAndUnknownKeyDoesNotSend() throws Exception {
        PollingOrderEvent missingKey = order("old", signer("2"), false);
        PollingOrderEvent valid = order("slow", signer, false);
        setup(missingKey, valid);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

        try (var publisher = publisher()) {
            publisher.pollOnce();
        }
        verify(publications).retry(missingKey, 2000, "SIGNING_KEY_UNAVAILABLE");
        verify(publications).retry(valid, 2000, "KAFKA_ACK_TIMEOUT");
        verify(publications, never()).sent(any());
        verify(kafka).send(any(ProducerRecord.class));
    }

    @Test
    void terminalKafkaErrorQuarantinesWithNoSentTransition() throws Exception {
        PollingOrderEvent event = order("oversize", signer, false);
        setup(event);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RecordTooLargeException()));

        try (var publisher = publisher()) {
            publisher.pollOnce();
        }
        verify(publications).quarantine(event, "KAFKA_TERMINAL_RECORD");
        verify(publications, never()).sent(any());
    }

    @Test
    void losingTheClaimCannotPublishAndPollingRequiresTheOperatorGate() {
        when(publications.due(settings.batchSize())).thenReturn(List.of("lost"));
        when(publications.claim("lost", settings.leaseMs())).thenReturn(Optional.empty());
        try (var publisher = publisher()) {
            publisher.pollOnce();
        }
        verifyNoInteractions(kafka);
        assertThrows(IllegalArgumentException.class, () -> new OrderOutboxPollingProperties(
                OrderOutboxPollingProperties.Mode.POLLING, false, 1000, 25, 200, 500, 1000, 120000));
        assertFalse(PollingOrderEvent.supports("ORDER_COMPLETED", "inventory.completed"));
    }

    private OrderOutboxPollingPublisher publisher() {
        return new OrderOutboxPollingPublisher(publications, records, kafka, settings, () -> { });
    }

    private void setup(PollingOrderEvent... events) {
        when(publications.due(settings.batchSize())).thenReturn(
                List.of(events).stream().map(PollingOrderEvent::outboxId).toList());
        for (PollingOrderEvent event : events) {
            when(publications.claim(event.outboxId(), settings.leaseMs())).thenReturn(Optional.of(event));
        }
    }

    private PollingOrderEvent order(String id, OrderKafkaSigner key, boolean tamper) throws Exception {
        String orderId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", "ORDER_COMPLETED");
        body.put("eventVersion", 1);
        body.put("eventKey", orderId);
        body.put("orderId", orderId);
        body.put("sourceService", "order-service");
        body.put("customerId", null);
        body.put("orderStatus", "COMPLETED");
        body.put("paymentStatus", "PAID");
        body.put("occurredAt", OCCURRED.toString());
        body.put("completedAt", OCCURRED.minusSeconds(10).toString());
        body.put("correlationId", orderId);
        body.put("items", List.of(Map.of("orderItemId", UUID.randomUUID().toString(),
                "productVariantId", UUID.randomUUID().toString(), "purchaseType", "PRODUCT",
                "quantity", new BigDecimal("2.125"))));
        key.sign(TOPIC, orderId, body);
        if (tamper) {
            body.put("completedAt", OCCURRED.minusSeconds(12).toString());
        }
        return new PollingOrderEvent(id, "token", 2, eventId, "ORDER_COMPLETED", 1, "ORDER",
                orderId, orderId, orderId + ":order-completed", TOPIC, "order-service",
                mapper.writeValueAsString(body), OCCURRED, null, orderId, null);
    }

    private OrderKafkaSigner signer(String version) {
        return new OrderKafkaSigner(mapper, "isolated-test", version, Base64.getEncoder().encodeToString(new byte[32]));
    }

    private static CompletableFuture<SendResult<String, String>> ack(ProducerRecord<String, String> record) {
        var metadata = new RecordMetadata(new TopicPartition(record.topic(), 0), 4, 0, 0, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(record, metadata));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
