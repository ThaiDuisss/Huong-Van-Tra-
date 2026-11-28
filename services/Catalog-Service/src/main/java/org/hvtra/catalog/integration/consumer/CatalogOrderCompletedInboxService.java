package org.hvtra.catalog.integration.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.catalog.entity.CatalogInbox;
import org.hvtra.catalog.integration.event.OrderCompletedCatalogEvent;
import org.hvtra.catalog.repository.CatalogInboxRepository;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Nhận event ORDER_COMPLETED từ Order-Service qua Transactional Inbox để lưu Product/Variant/BOM CUSTOMER idempotent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogOrderCompletedInboxService {

    private static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    private static final String SOURCE_SERVICE = "order-service";
    private static final String UNKNOWN_EVENT = "UNKNOWN_ORDER_EVENT";

    private final DebeziumOutboxEventPayloadParser payloadParser;
    private final EventMetadataResolver metadataResolver;
    private final CatalogInboxRepository inboxRepository;

    @Value("${kafka-consumer-config.group-id:catalog-service-order-events}")
    private String consumerGroup;

    @Transactional
    public void handle(ConsumerRecord<String, String> record, Predicate<OrderCompletedCatalogEvent> handler) {
        Optional<OrderCompletedCatalogEvent> parsed = payloadParser.parseCreateEvent(
                record.value(), OrderCompletedCatalogEvent.class, ORDER_COMPLETED, null);
        if (parsed.isEmpty()) {
            log.debug("Bỏ qua record không phải outbox ORDER_COMPLETED. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        OrderCompletedCatalogEvent event = parsed.get();
        metadataResolver.normalizeBeforeEventId(record, event, topic -> ORDER_COMPLETED, UNKNOWN_EVENT, ORDER_COMPLETED);
        metadataResolver.normalizeEventKey(record, event);
        String eventId = metadataResolver.resolveEventId(record, event, ORDER_COMPLETED);

        Optional<CatalogInbox> existing = inboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            log.debug("Event {} đã xử lý trước đó (status={}), bỏ qua", eventId, existing.get().getStatus());
            return;
        }

        String entityId = Objects.toString(event.getOrderId(), null);
        CatalogInbox inbox = inboxRepository.save(buildInbox(record, event, eventId, entityId));

        boolean processed = ORDER_COMPLETED.equals(event.getEventType()) && handler.test(event);
        inbox.setStatus(processed ? InboxStatus.PROCESSED : InboxStatus.IGNORED);
        inbox.setProcessedAt(Instant.now());
        inboxRepository.save(inbox);
    }

    private CatalogInbox buildInbox(
            ConsumerRecord<String, String> record,
            OrderCompletedCatalogEvent event,
            String eventId,
            String entityId) {
        CatalogInbox inbox = new CatalogInbox();
        inbox.setEventId(eventId);
        inbox.setConsumerGroup(consumerGroup);
        inbox.setTopic(record.topic());
        inbox.setPartitionNo(record.partition());
        inbox.setOffsetNo(record.offset());
        inbox.setMessageKey(record.key());
        inbox.setEventType(event.getEventType());
        inbox.setEventVersion(1);
        inbox.setSourceService(metadataResolver.sourceService(record, SOURCE_SERVICE));
        inbox.setEntityType("ORDER");
        inbox.setEntityId(entityId);
        inbox.setCorrelationId(metadataResolver.correlationId(record, entityId));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(metadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.PROCESSING);
        return inbox;
    }
}
