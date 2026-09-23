package org.hvtra.iventoryservice.outbox.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.iventoryservice.outbox.entity.InventoryOutboxEvent;
import org.hvtra.iventoryservice.outbox.event.InventoryEventType;
import org.hvtra.iventoryservice.outbox.resolver.InventoryOutboxTopicResolver;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryOutboxPublisher {

    private static final String SOURCE = "inventory-service";
    private static final String AGGREGATE_TYPE = "ORDER_RETURN_INVENTORY_COMMAND";

    private final OutboxEventService<InventoryOutboxEvent> inventoryOutboxEventService;
    private final InventoryOutboxTopicResolver inventoryOutboxTopicResolver;

    public void publishReturnInventoryDispositionProcessed(
            String commandId,
            String returnId,
            String inspectionId,
            String sagaId,
            String correlationId
    ) {
        String eventId = UUID.randomUUID().toString();
        String eventType = InventoryEventType.RETURN_INVENTORY_DISPOSITION_PROCESSED;
        String businessKey = commandId + ":PROCESSED";
        String topic = inventoryOutboxTopicResolver.resolveTopic(eventType);
        Instant now = Instant.now();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("commandId", commandId);
        payload.put("returnId", returnId);
        payload.put("inspectionId", inspectionId);
        payload.put("processedAt", now.toString());

        Map<String, Object> headers = buildHeaders(
                eventId,
                eventType,
                commandId,
                businessKey,
                sagaId,
                correlationId,
                commandId
        );

        inventoryOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                AGGREGATE_TYPE,
                commandId,
                commandId,
                eventType,
                businessKey,
                topic,
                SOURCE,
                sagaId,
                correlationId,
                commandId,
                payload,
                headers
        ));

        log.info("Published outbox {}. commandId={}, returnId={}, inspectionId={}, eventId={}",
                eventType, commandId, returnId, inspectionId, eventId);
    }

    public void publishReturnInventoryDispositionFailed(
            String commandId,
            String returnId,
            String failureCode,
            String failureReason,
            String sagaId,
            String correlationId
    ) {
        String eventId = UUID.randomUUID().toString();
        String eventType = InventoryEventType.RETURN_INVENTORY_DISPOSITION_FAILED;
        String businessKey = commandId + ":FAILED";
        String topic = inventoryOutboxTopicResolver.resolveTopic(eventType);
        Instant now = Instant.now();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("commandId", commandId);
        payload.put("returnId", returnId);
        payload.put("failureCode", failureCode);
        payload.put("failureReason", failureReason);
        payload.put("failedAt", now.toString());

        Map<String, Object> headers = buildHeaders(
                eventId,
                eventType,
                commandId,
                businessKey,
                sagaId,
                correlationId,
                commandId
        );

        inventoryOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                AGGREGATE_TYPE,
                commandId,
                commandId,
                eventType,
                businessKey,
                topic,
                SOURCE,
                sagaId,
                correlationId,
                commandId,
                payload,
                headers
        ));

        log.info("Published outbox {}. commandId={}, returnId={}, failureCode={}, eventId={}",
                eventType, commandId, returnId, failureCode, eventId);
    }

    private Map<String, Object> buildHeaders(
            String eventId,
            String eventType,
            String aggregateId,
            String businessKey,
            String sagaId,
            String correlationId,
            String causationId
    ) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("event_id", eventId);
        headers.put("event_type", eventType);
        headers.put("event_version", 1);
        headers.put("source_service", SOURCE);
        headers.put("saga_id", sagaId);
        headers.put("correlation_id", correlationId);
        headers.put("causation_id", causationId);
        headers.put("aggregate_type", AGGREGATE_TYPE);
        headers.put("aggregate_id", aggregateId);
        headers.put("business_key", businessKey);
        return headers;
    }
}
