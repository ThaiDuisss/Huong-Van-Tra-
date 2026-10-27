package org.hvtra.orderservice.integration.inventory.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.orderservice.domain.entity.OrderInbox;
import org.hvtra.orderservice.integration.inventory.dto.CustomBundlePackedEvent;
import org.hvtra.orderservice.repository.OrderInboxRepository;
import org.hvtra.orderservice.service.OrderCustomBundleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Nhận CUSTOM_BUNDLE_PACKED (idempotent qua inbox) và đánh dấu dòng bundle đã đóng gói. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomBundlePackedInboxService {

    private static final String EVENT_TYPE = "CUSTOM_BUNDLE_PACKED";

    private final OrderInboxRepository orderInboxRepository;
    private final DebeziumOutboxEventPayloadParser parser;
    private final EventMetadataResolver metadataResolver;
    private final OrderCustomBundleService orderCustomBundleService;

    @Value("${order.inventory.events.custom-bundle-packed.group-id:order-custom-bundle-packed-events}")
    private String consumerGroup;

    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        parser.parseCreateEvent(
                        record.value(),
                        CustomBundlePackedEvent.class,
                        "custom bundle packed event",
                        (event, metadata) -> {
                            if (isBlank(event.getPackingId())) {
                                event.setPackingId(metadata.aggregateId());
                            }
                            if (isBlank(event.getEventId())) {
                                event.setEventId(metadata.eventId());
                            }
                        })
                .filter(event -> EVENT_TYPE.equals(event.getEventType()))
                .ifPresent(event -> handleEvent(record, event));
    }

    private void handleEvent(ConsumerRecord<String, String> record, CustomBundlePackedEvent event) {
        metadataResolver.normalizeBeforeEventId(record, event, topic -> EVENT_TYPE, EVENT_TYPE, "custom bundle packed event");
        metadataResolver.normalizeEventKey(record, event);
        String eventId = metadataResolver.resolveEventId(record, event, "Custom bundle packed event");

        OrderInbox inbox = claimInbox(record, event, eventId);
        if (inbox.getStatus() == InboxStatus.PROCESSED || inbox.getStatus() == InboxStatus.IGNORED
                || inbox.getStatus() == InboxStatus.FAILED || inbox.getStatus() == InboxStatus.DEAD) {
            log.debug("Duplicate custom bundle packed event already handled. eventId={}", eventId);
            return;
        }
        inbox.setStatus(InboxStatus.PROCESSING);

        UUID orderId = parseUuid(event.getOrderId());
        UUID orderItemId = parseUuid(event.getOrderItemId());
        if (orderId == null || orderItemId == null) {
            finish(inbox, InboxStatus.FAILED, "Missing or invalid orderId/orderItemId");
            return;
        }
        boolean updated = orderCustomBundleService.markPacked(orderId, orderItemId, event.getPackingCode());
        log.info("Custom bundle packed. orderCode={}, packingCode={}, updated={}",
                event.getOrderCode(), event.getPackingCode(), updated);
        finish(inbox, updated ? InboxStatus.PROCESSED : InboxStatus.IGNORED, null);
    }

    private OrderInbox claimInbox(ConsumerRecord<String, String> record, CustomBundlePackedEvent event, String eventId) {
        Optional<OrderInbox> existing = orderInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            return existing.get();
        }
        OrderInbox inbox = new OrderInbox();
        inbox.setEventId(eventId);
        inbox.setConsumerGroup(consumerGroup);
        inbox.setTopic(record.topic());
        inbox.setPartitionNo(record.partition());
        inbox.setOffsetNo(record.offset());
        inbox.setMessageKey(record.key());
        inbox.setEventType(event.getEventType());
        inbox.setEventVersion(1);
        inbox.setSourceService(metadataResolver.sourceService(record, "inventory-service"));
        inbox.setEntityType("CUSTOM_BUNDLE_PACKING");
        inbox.setEntityId(event.getPackingId());
        inbox.setCorrelationId(metadataResolver.correlationId(record, record.key()));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(metadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.RECEIVED);
        inbox.setReceivedAt(Instant.now());
        return orderInboxRepository.saveAndFlush(inbox);
    }

    private void finish(OrderInbox inbox, InboxStatus status, String error) {
        inbox.setStatus(status);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(error);
        orderInboxRepository.saveAndFlush(inbox);
    }

    private static UUID parseUuid(String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
