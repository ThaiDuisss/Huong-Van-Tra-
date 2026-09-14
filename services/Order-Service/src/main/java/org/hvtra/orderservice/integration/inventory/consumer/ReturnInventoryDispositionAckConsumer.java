package org.hvtra.orderservice.integration.inventory.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class ReturnInventoryDispositionAckConsumer {

    private final ReturnInventoryDispositionAckInboxService ackInboxService;

    @KafkaListener(
            topics = {
                    "${order.inventory.events.topics.return-disposition-processed:inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED}",
                    "${order.inventory.events.topics.return-disposition-failed:inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED}"
            },
            groupId = "${order.inventory.events.return-disposition-ack.group-id:order-return-inventory-ack-events}"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.info("ReturnInventoryDispositionAckConsumer received record on topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());
        ackInboxService.handle(record);
        acknowledgment.acknowledge();
    }
}
