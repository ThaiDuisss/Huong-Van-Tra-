package org.hvtra.iventoryservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class InventoryReservationConfirmConsumer {

    private final InventoryReservationConfirmInboxService inventoryReservationConfirmInboxService;

    @KafkaListener(
            topics = "${inventory.order.events.topics.reservation-confirm:inventory.reservation-confirm.requested}",
            groupId = "${spring.application.name:inventory-service}-order-events"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        inventoryReservationConfirmInboxService.handle(record);
        acknowledgment.acknowledge();
    }
}
