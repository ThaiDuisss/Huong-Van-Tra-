package org.hvtra.iventoryservice.bundle.service;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.iventoryservice.domain.entity.bundle.CustomBundlePacking;
import org.hvtra.iventoryservice.outbox.entity.InventoryOutboxEvent;
import org.hvtra.iventoryservice.outbox.event.InventoryEventType;
import org.hvtra.iventoryservice.outbox.resolver.InventoryOutboxTopicResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Ghi sự kiện đóng trà theo yêu cầu vào outbox (Debezium đẩy sang Kafka theo cột topic). */
@Slf4j
@Component
public class CustomBundleEventPublisher {

    private static final String SOURCE = "inventory-service";
    private static final String AGGREGATE_TYPE = "CUSTOM_BUNDLE_PACKING";

    private final OutboxEventService<InventoryOutboxEvent> outboxEventService;
    private final InventoryOutboxTopicResolver topicResolver;

    public CustomBundleEventPublisher(
            OutboxEventService<InventoryOutboxEvent> outboxEventService,
            InventoryOutboxTopicResolver topicResolver) {
        this.outboxEventService = outboxEventService;
        this.topicResolver = topicResolver;
    }

    public void publishPacked(CustomBundlePacking packing) {
        String eventId = UUID.randomUUID().toString();
        String eventType = InventoryEventType.CUSTOM_BUNDLE_PACKED;
        String packingId = packing.getId().toString();
        String orderId = packing.getOrderId().toString();
        String businessKey = packingId + ":PACKED";
        String correlationId = currentCorrelationId();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("packingId", packingId);
        payload.put("packingCode", packing.getPackingCode());
        payload.put("orderId", orderId);
        payload.put("orderCode", packing.getOrderCode());
        payload.put("orderItemId", packing.getOrderItemId().toString());
        payload.put("issueTransactionCode", packing.getIssueTransactionCode());
        payload.put("packedAt", packing.getPackedAt() == null ? null : packing.getPackedAt().toString());

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("event_id", eventId);
        headers.put("event_type", eventType);
        headers.put("event_version", 1);
        headers.put("source_service", SOURCE);
        headers.put("saga_id", null);
        headers.put("correlation_id", correlationId);
        headers.put("causation_id", packingId);
        headers.put("aggregate_type", AGGREGATE_TYPE);
        headers.put("aggregate_id", packingId);
        headers.put("business_key", businessKey);

        // messageKey = orderId: mọi sự kiện của cùng một đơn vào cùng partition, giữ đúng thứ tự.
        outboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                AGGREGATE_TYPE,
                packingId,
                orderId,
                eventType,
                businessKey,
                topicResolver.resolveTopic(eventType),
                SOURCE,
                null,
                correlationId,
                packingId,
                payload,
                headers
        ));
        log.info("Published outbox {}. packingCode={}, orderCode={}, eventId={}",
                eventType, packing.getPackingCode(), packing.getOrderCode(), eventId);
    }

    private static String currentCorrelationId() {
        try {
            return SecurityUtils.currentCorrelationId();
        } catch (AccessDeniedException exception) {
            return null;
        }
    }
}
