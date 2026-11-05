package org.hvtra.iventoryservice.integration.catalog.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class CatalogItemSyncConsumer {

    private final CatalogItemSyncInboxService catalogItemSyncInboxService;

    @KafkaListener(
            topics = "${inventory.catalog.events.item-sync.topics.item-sync:catalog.event.ITEM_SYNC}",
            groupId = "${inventory.catalog.events.item-sync.group-id:inventory-catalog-item-sync}"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        catalogItemSyncInboxService.handle(record);
        acknowledgment.acknowledge();
    }
}

