package org.hvtra.iventoryservice.integration.catalog.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.iventoryservice.domain.entity.InventoryInbox;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.integration.catalog.dto.CatalogItemSyncPayload;
import org.hvtra.iventoryservice.integration.order.repository.InventoryInboxRepository;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogItemSyncInboxService {

    private final InventoryInboxRepository inventoryInboxRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final CatalogItemSyncPayloadParser catalogItemSyncPayloadParser;
    private final CatalogItemSyncMetadataResolver catalogItemSyncMetadataResolver;

    @Value("${inventory.catalog.events.item-sync.group-id:inventory-catalog-item-sync}")
    private String consumerGroup;

    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        Optional<CatalogItemSyncPayload> parsedEvent = catalogItemSyncPayloadParser.parseCreateEvent(record.value());
        if (parsedEvent.isEmpty()) {
            log.debug("Skip non-create Debezium event for catalog sync. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        CatalogItemSyncPayload event = parsedEvent.get();
        catalogItemSyncMetadataResolver.normalizeBeforeEventId(record, event);
        String eventId = catalogItemSyncMetadataResolver.resolveEventId(record, event);
        catalogItemSyncMetadataResolver.normalizeEventKey(record, event);

        InventoryInbox inbox = insertInbox(record, event, eventId);
        if (isTerminal(inbox.getStatus())) {
            return;
        }

        try {
            inbox.setStatus(InboxStatus.PROCESSING);
            processItemSync(event);
            markProcessed(inbox);
        } catch (Exception ex) {
            log.error("Failed to process catalog sync event. eventId={}, referenceId={}, eventType={}",
                    eventId, event.getReferenceId(), event.getEventType(), ex);
            markFailed(inbox, ex);
            throw ex;
        }
    }

    private void processItemSync(CatalogItemSyncPayload event) {
        if (event.getReferenceId() == null || event.getItemType() == null) {
            log.warn("Catalog sync event missing referenceId or itemType: {}", event);
            return;
        }

        UUID referenceId = UUID.fromString(event.getReferenceId());
        InventoryItemType itemType = InventoryItemType.valueOf(event.getItemType());
        boolean isDelete = Boolean.TRUE.equals(event.getIsDeleted())
                || (event.getEventType() != null && event.getEventType().endsWith("_DELETED"));

        Optional<InventoryItem> optionalItem = inventoryItemRepository.findByReferenceIdAndItemType(referenceId, itemType);

        if (isDelete) {
            optionalItem.ifPresent(item -> {
                item.setIsDeleted(true);
                item.setStatus(InventoryStatus.INACTIVE);
                inventoryItemRepository.save(item);
                log.info("Soft-deleted InventoryItem for {} referenceId={}", itemType, referenceId);
            });
            return;
        }

        InventoryItem item = optionalItem.orElseGet(() -> {
            InventoryItem newItem = new InventoryItem();
            newItem.setReferenceId(referenceId);
            newItem.setItemType(itemType);
            return newItem;
        });

        item.setIsDeleted(false);
        if (event.getName() != null) {
            item.setName(event.getName());
        }
        if (event.getReferenceCode() != null) {
            item.setReferenceCode(event.getReferenceCode());
        }
        if (event.getUnitId() != null && !event.getUnitId().isBlank()) {
            item.setUnitId(UUID.fromString(event.getUnitId()));
        }
        if (event.getStatus() != null && !event.getStatus().isBlank()) {
            try {
                item.setStatus(InventoryStatus.valueOf(event.getStatus()));
            } catch (IllegalArgumentException e) {
                item.setStatus(InventoryStatus.ACTIVE);
            }
        } else if (item.getStatus() == null) {
            item.setStatus(InventoryStatus.ACTIVE);
        }
        if (event.getReorderPoint() != null) {
            item.setReorderPoint(event.getReorderPoint());
        } else if (item.getReorderPoint() == null) {
            item.setReorderPoint(0);
        }

        inventoryItemRepository.save(item);
        log.info("Synchronized InventoryItem for {} referenceId={}, code={}", itemType, referenceId, item.getReferenceCode());
    }

    private InventoryInbox insertInbox(
            ConsumerRecord<String, String> record,
            CatalogItemSyncPayload event,
            String eventId
    ) {
        Optional<InventoryInbox> existing = inventoryInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            InventoryInbox inbox = existing.get();
            if (isTerminal(inbox.getStatus())) {
                log.debug("Duplicate catalog sync event already handled. eventId={}, consumerGroup={}", eventId, consumerGroup);
                return inbox;
            }

            throw new IllegalStateException(
                    "Duplicate catalog sync event is not completed yet. eventId=" + eventId + ", status=" + inbox.getStatus()
            );
        }

        InventoryInbox inbox = buildInbox(record, event, eventId);
        return inventoryInboxRepository.saveAndFlush(inbox);
    }

    private InventoryInbox buildInbox(
            ConsumerRecord<String, String> record,
            CatalogItemSyncPayload event,
            String eventId
    ) {
        InventoryInbox inbox = new InventoryInbox();

        inbox.setEventId(eventId);
        inbox.setConsumerGroup(consumerGroup);
        inbox.setTopic(record.topic());
        inbox.setPartitionNo(record.partition());
        inbox.setOffsetNo(record.offset());
        inbox.setMessageKey(record.key());
        inbox.setEventType(event.getEventType());
        inbox.setEventVersion(1);
        inbox.setSourceService(catalogItemSyncMetadataResolver.sourceService(record));
        inbox.setEntityType(event.getItemType() != null ? event.getItemType() : "CATALOG_ITEM");
        inbox.setEntityId(event.getReferenceId());
        inbox.setCorrelationId(catalogItemSyncMetadataResolver.correlationId(record, event));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(catalogItemSyncMetadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.RECEIVED);

        return inbox;
    }

    private void markProcessed(InventoryInbox inbox) {
        inbox.setStatus(InboxStatus.PROCESSED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
    }

    private void markFailed(InventoryInbox inbox, Exception ex) {
        inbox.setStatus(InboxStatus.FAILED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(ex.getMessage());
    }

    private boolean isTerminal(InboxStatus status) {
        return status == InboxStatus.PROCESSED
                || status == InboxStatus.IGNORED
                || status == InboxStatus.FAILED
                || status == InboxStatus.DEAD;
    }
}

