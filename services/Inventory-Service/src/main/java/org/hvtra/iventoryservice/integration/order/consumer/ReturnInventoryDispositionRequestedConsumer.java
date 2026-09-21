package org.hvtra.iventoryservice.integration.order.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class ReturnInventoryDispositionRequestedConsumer {

    private final ReturnInventoryDispositionInboxService inboxService;

    @KafkaListener(
            topics = "${inventory.order.events.topics.return-disposition-requested:order.event.RETURN_INVENTORY_DISPOSITION_REQUESTED}",
            groupId = "${inventory.order.events.return-disposition.group-id:inventory-return-disposition}"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        inboxService.handle(record);
        acknowledgment.acknowledge();
    }
}
