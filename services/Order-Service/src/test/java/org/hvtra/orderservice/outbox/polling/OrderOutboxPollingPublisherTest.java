package org.hvtra.orderservice.outbox.polling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.orderservice.outbox.event.OrderEventType;
import org.hvtra.orderservice.outbox.factory.OrderOutboxEventFactory;
import org.hvtra.orderservice.outbox.service.OrderKafkaSigner;
import org.hvtra.orderservice.repository.OrderOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import static org.mockito.Mockito.times;
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

    @ParameterizedTest
    @CsvSource({
            "RETURN_CREATED,notification.return.created,:return-created,CREATED",
            "RETURN_RECEIVED,notification.return.received,:return-received,RECEIVED",
            "RETURN_CANCELLED,notification.return.cancelled,:return-cancelled,CANCELLED"
    })
    void sendsReturnLifecycleNotificationsWithExpectedTopicKeyAndHeaders(
            String eventType,
            String topic,
            String businessKeySuffix,
            String newStatus
    ) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String returnId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String actorId = UUID.randomUUID().toString();
        String businessKey = returnId + businessKeySuffix;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", eventType);
        body.put("eventKey", returnId);
        body.put("returnId", returnId);
        body.put("orderId", orderId);
        body.put("newStatus", newStatus);
        body.put("occurredAt", OCCURRED.toString());
        body.put("items", List.of(Map.of("returnItemId", UUID.randomUUID().toString(),
                "orderItemId", UUID.randomUUID().toString(), "quantity", new BigDecimal("1.000"))));
        String payload = mapper.writeValueAsString(body);
        PollingOrderEvent event = new PollingOrderEvent("return-notif", "token", 1, eventId, eventType, 1,
                "ORDER_RETURN", returnId, returnId, businessKey, topic, "order-service", payload, OCCURRED,
                null, returnId, actorId);

        var record = records.create(event);
        assertEquals(topic, record.topic());
        assertEquals(returnId, record.key());
        assertEquals(payload, record.value());
        assertEquals(eventId, header(record, "event_id"));
        assertEquals(eventType, header(record, "event_type"));
        assertEquals("ORDER_RETURN", header(record, "aggregate_type"));
        assertEquals(returnId, header(record, "aggregate_id"));
        assertEquals(businessKey, header(record, "business_key"));
        assertEquals(returnId, header(record, "correlation_id"));
        assertEquals(actorId, header(record, "causation_id"));
    }

    @Test
    void supportsAllReturnAndRefundEventsWhileKeepingUnrelatedEventsSeparate() {
        assertFalse(PollingOrderEvent.supports("ORDER_PAYMENT_SUCCEEDED", "notification.order.payment-succeeded"));
        assertFalse(PollingOrderEvent.supports("ORDER_COMPLETED", "inventory.completed"));
    }

    @Test
    void transactionalServiceEnqueuesAllReturnAndRefundEventsWithoutEnqueuingForwardPaymentEvents() {
        OrderOutboxEventRepository repo = mock(OrderOutboxEventRepository.class);
        OrderOutboxEventFactory factory = new OrderOutboxEventFactory(mapper);
        TransactionalOrderOutboxEventService service = new TransactionalOrderOutboxEventService(
                repo, factory, publications, settings);

        Map<String, String> returnTopics = Map.of(
                OrderEventType.RETURN_CREATED, "notification.return.created",
                OrderEventType.RETURN_RECEIVED, "notification.return.received",
                OrderEventType.RETURN_CANCELLED, "notification.return.cancelled",
                OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED,
                "order.event.RETURN_INVENTORY_DISPOSITION_REQUESTED",
                OrderEventType.RETURN_REFUND_COMPLETED, "notification.return.refund-completed",
                OrderEventType.ORDER_REFUNDED, "customer.order-refunded",
                OrderEventType.RETURN_COMPLETED, "notification.return.completed"
        );

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            for (var entry : returnTopics.entrySet()) {
                String eventId = UUID.randomUUID().toString();
                String aggregateId = UUID.randomUUID().toString();
                service.saveEvent(new OutboxSaveCommand(
                        eventId, "ORDER_RETURN", aggregateId, aggregateId,
                        entry.getKey(), aggregateId + ":" + entry.getKey(),
                        entry.getValue(), "order-service", null, aggregateId, null,
                        Map.of("eventId", eventId), Map.of()));
                verify(publications).enqueue(eventId);
            }

            String paymentEventId = UUID.randomUUID().toString();
            String orderId = UUID.randomUUID().toString();
            service.saveEvent(new OutboxSaveCommand(
                    paymentEventId, "ORDER", orderId, orderId,
                    OrderEventType.ORDER_PAYMENT_SUCCEEDED, orderId + ":paid",
                    "notification.order.payment-succeeded", "order-service", null, orderId, null,
                    Map.of("eventId", paymentEventId), Map.of()));
            verify(publications, never()).enqueue(paymentEventId);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(publications, times(7)).enqueue(any());
    }

    @Test
    void acceptsSignedCustomBundleOrderCompletedWithComponentsAndRejectsMissingComponents() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", "ORDER_COMPLETED");
        body.put("eventVersion", 1);
        body.put("eventKey", orderId);
        body.put("orderId", orderId);
        body.put("sourceService", "order-service");
        body.put("customerId", UUID.randomUUID().toString());
        body.put("orderStatus", "COMPLETED");
        body.put("paymentStatus", "PAID");
        body.put("occurredAt", OCCURRED.toString());
        body.put("completedAt", OCCURRED.minusSeconds(5).toString());
        body.put("correlationId", orderId);
        body.put("items", List.of(Map.of(
                "orderItemId", UUID.randomUUID().toString(),
                "productVariantId", UUID.randomUUID().toString(),
                "purchaseType", "CUSTOM_BUNDLE",
                "quantity", new BigDecimal("1"),
                "components", List.of(Map.of(
                        "ingredientId", UUID.randomUUID().toString(),
                        "ingredientCode", "LOLTQX-G-48",
                        "quantity", new BigDecimal("50.000"),
                        "unit", "g"
                )))));
        signer.sign(TOPIC, orderId, body);
        PollingOrderEvent validEvent = new PollingOrderEvent("bundle-valid", "token", 1, eventId, "ORDER_COMPLETED",
                1, "ORDER", orderId, orderId, orderId + ":order-completed", TOPIC, "order-service",
                mapper.writeValueAsString(body), OCCURRED, null, orderId, null);

        var record = records.create(validEvent);
        assertEquals(TOPIC, record.topic());

        Map<String, Object> missingComponents = new LinkedHashMap<>(body);
        missingComponents.remove("kafkaAuth");
        missingComponents.put("items", List.of(Map.of(
                "orderItemId", UUID.randomUUID().toString(),
                "productVariantId", UUID.randomUUID().toString(),
                "purchaseType", "CUSTOM_BUNDLE",
                "quantity", new BigDecimal("1"))));
        signer.sign(TOPIC, orderId, missingComponents);
        PollingOrderEvent invalidEvent = new PollingOrderEvent("bundle-missing", "token", 1, eventId, "ORDER_COMPLETED",
                1, "ORDER", orderId, orderId, orderId + ":order-completed", TOPIC, "order-service",
                mapper.writeValueAsString(missingComponents), OCCURRED, null, orderId, null);

        var rejected = assertThrows(OrderOutboxRecordFactory.RejectedEvent.class, () -> records.create(invalidEvent));
        assertEquals("INVALID_PURCHASE_LINE", rejected.getMessage());
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
