package org.hvtra.iventoryservice.outbox.polling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.iventoryservice.outbox.event.InventoryEventType;
import org.hvtra.iventoryservice.outbox.factory.InventoryOutboxEventFactory;
import org.hvtra.iventoryservice.outbox.repository.InventoryOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryOutboxPollingPublisherTest {
    private static final Instant OCCURRED = Instant.parse("2026-11-26T10:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final InventoryOutboxRecordFactory records = new InventoryOutboxRecordFactory(mapper);
    private final InventoryOutboxPublicationStore publications = mock(InventoryOutboxPublicationStore.class);
    @SuppressWarnings("unchecked")
    private final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
    private final InventoryOutboxPollingProperties settings = new InventoryOutboxPollingProperties(
            InventoryOutboxPollingProperties.Mode.POLLING, true, 1000, 25, 200, 500, 1000, 120000);

    @Test
    void sendsReturnDispositionProcessedAndFailedWithExpectedTopicsKeysAndHeaders() throws Exception {
        PollingInventoryEvent processed = processedEvent("processed-1");
        PollingInventoryEvent failed = failedEvent("failed-1");

        ProducerRecord<String, String> processedRecord = records.create(processed);
        assertEquals("inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED", processedRecord.topic());
        assertEquals(processed.key(), processedRecord.key());
        assertEquals(processed.eventId(), header(processedRecord, "event_id"));
        assertEquals("RETURN_INVENTORY_DISPOSITION_PROCESSED", header(processedRecord, "event_type"));
        assertEquals(processed.businessKey(), header(processedRecord, "business_key"));

        ProducerRecord<String, String> failedRecord = records.create(failed);
        assertEquals("inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED", failedRecord.topic());
        assertEquals(failed.key(), failedRecord.key());
        assertEquals(failed.eventId(), header(failedRecord, "event_id"));
        assertEquals("RETURN_INVENTORY_DISPOSITION_FAILED", header(failedRecord, "event_type"));
        assertEquals(failed.businessKey(), header(failedRecord, "business_key"));
    }

    @Test
    void ackMarksSentWhileInvalidContractQuarantinesAndTimeoutRetries() throws Exception {
        PollingInventoryEvent invalid = processedEvent("bad-row");
        PollingInventoryEvent invalidWithWrongKey = new PollingInventoryEvent(
                invalid.outboxId(), invalid.claimToken(), invalid.attempts(), invalid.eventId(),
                invalid.eventType(), invalid.eventVersion(), invalid.aggregateType(), invalid.aggregateId(),
                UUID.randomUUID().toString(), invalid.businessKey(), invalid.topic(), invalid.source(),
                invalid.sagaId(), invalid.correlationId(), invalid.causationId(), invalid.payload(), invalid.occurredAt());
        PollingInventoryEvent valid = processedEvent("good-row");
        PollingInventoryEvent timeout = failedEvent("timeout-row");

        when(publications.due(settings.batchSize())).thenReturn(List.of(
                invalidWithWrongKey.outboxId(), valid.outboxId(), timeout.outboxId()));
        when(publications.claim(invalidWithWrongKey.outboxId(), settings.leaseMs()))
                .thenReturn(Optional.of(invalidWithWrongKey));
        when(publications.claim(valid.outboxId(), settings.leaseMs())).thenReturn(Optional.of(valid));
        when(publications.claim(timeout.outboxId(), settings.leaseMs())).thenReturn(Optional.of(timeout));

        when(kafka.send(any(ProducerRecord.class)))
                .thenAnswer(call -> ack(call.getArgument(0)))
                .thenReturn(new CompletableFuture<>());
        when(publications.sent(valid)).thenReturn(true);

        try (InventoryOutboxPollingPublisher publisher = publisher()) {
            publisher.pollOnce();
        }

        var ordered = inOrder(publications, kafka);
        ordered.verify(publications).quarantine(invalidWithWrongKey, "INVALID_RETURN_ACK_CONTRACT");
        ordered.verify(kafka).send(any(ProducerRecord.class));
        ordered.verify(publications).sent(valid);
        verify(publications).retry(timeout, 2000L, "KAFKA_ACK_TIMEOUT");
        verify(publications, never()).sent(invalidWithWrongKey);
        verify(publications, never()).sent(timeout);
    }

    @Test
    void terminalKafkaErrorQuarantinesAndLostClaimSkipsSend() throws Exception {
        PollingInventoryEvent oversize = processedEvent("oversize");
        when(publications.due(settings.batchSize())).thenReturn(List.of("lost", oversize.outboxId()));
        when(publications.claim("lost", settings.leaseMs())).thenReturn(Optional.empty());
        when(publications.claim(oversize.outboxId(), settings.leaseMs())).thenReturn(Optional.of(oversize));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RecordTooLargeException()));

        try (InventoryOutboxPollingPublisher publisher = publisher()) {
            publisher.pollOnce();
        }
        verify(publications).quarantine(oversize, "KAFKA_TERMINAL_RECORD");
        verify(publications, never()).sent(oversize);
    }

    @Test
    void pollingRequiresCdcFenceAndTransactionalServiceEnqueuesSupportedEvents() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryOutboxPollingProperties(
                InventoryOutboxPollingProperties.Mode.POLLING, false, 1000, 25, 200, 500, 1000, 120000));
        assertTrue(PollingInventoryEvent.supports(
                InventoryEventType.RETURN_INVENTORY_DISPOSITION_PROCESSED,
                "inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED"));
        assertTrue(PollingInventoryEvent.supports(
                InventoryEventType.RETURN_INVENTORY_DISPOSITION_FAILED,
                "inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED"));
        assertFalse(PollingInventoryEvent.supports(
                InventoryEventType.RETURN_INVENTORY_DISPOSITION_PROCESSED,
                "inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED"));

        InventoryOutboxEventRepository repo = mock(InventoryOutboxEventRepository.class);
        InventoryOutboxEventFactory factory = new InventoryOutboxEventFactory(mapper);
        TransactionalInventoryOutboxEventService service = new TransactionalInventoryOutboxEventService(
                repo, factory, publications, settings);

        String eventId = UUID.randomUUID().toString();
        String cmdId = UUID.randomUUID().toString();
        OutboxSaveCommand cmd = new OutboxSaveCommand(
                eventId, "ORDER_RETURN_INVENTORY_COMMAND", cmdId, cmdId,
                InventoryEventType.RETURN_INVENTORY_DISPOSITION_PROCESSED,
                cmdId + ":PROCESSED",
                "inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED",
                "inventory-service", null, cmdId, cmdId,
                Map.of("commandId", cmdId), Map.of());

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.saveEvent(cmd);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(repo).saveAndFlush(any());
        verify(publications).enqueue(eventId);
    }

    private InventoryOutboxPollingPublisher publisher() {
        return new InventoryOutboxPollingPublisher(publications, records, kafka, settings, () -> { });
    }

    private PollingInventoryEvent processedEvent(String outboxId) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();
        String returnId = UUID.randomUUID().toString();
        String inspectionId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", InventoryEventType.RETURN_INVENTORY_DISPOSITION_PROCESSED);
        body.put("commandId", commandId);
        body.put("returnId", returnId);
        body.put("inspectionId", inspectionId);
        body.put("processedAt", OCCURRED.toString());
        return new PollingInventoryEvent(outboxId, "token", 2, eventId,
                InventoryEventType.RETURN_INVENTORY_DISPOSITION_PROCESSED, 1,
                "ORDER_RETURN_INVENTORY_COMMAND", commandId, commandId, commandId + ":PROCESSED",
                "inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED", "inventory-service",
                null, returnId, commandId, mapper.writeValueAsString(body), OCCURRED);
    }

    private PollingInventoryEvent failedEvent(String outboxId) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();
        String returnId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", InventoryEventType.RETURN_INVENTORY_DISPOSITION_FAILED);
        body.put("commandId", commandId);
        body.put("returnId", returnId);
        body.put("failureCode", "INSUFFICIENT_STOCK");
        body.put("failureReason", "Missing lot");
        body.put("failedAt", OCCURRED.toString());
        return new PollingInventoryEvent(outboxId, "token", 2, eventId,
                InventoryEventType.RETURN_INVENTORY_DISPOSITION_FAILED, 1,
                "ORDER_RETURN_INVENTORY_COMMAND", commandId, commandId, commandId + ":FAILED",
                "inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED", "inventory-service",
                null, returnId, commandId, mapper.writeValueAsString(body), OCCURRED);
    }

    private static CompletableFuture<SendResult<String, String>> ack(ProducerRecord<String, String> record) {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(record.topic(), 0), 1L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(record, metadata));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
