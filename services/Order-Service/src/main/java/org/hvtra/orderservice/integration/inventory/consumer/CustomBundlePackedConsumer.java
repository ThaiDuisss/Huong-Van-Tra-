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
public class CustomBundlePackedConsumer {

    private final CustomBundlePackedInboxService inboxService;

    @KafkaListener(
            topics = "${order.inventory.events.topics.custom-bundle-packed:inventory.event.CUSTOM_BUNDLE_PACKED}",
            groupId = "${order.inventory.events.custom-bundle-packed.group-id:order-custom-bundle-packed-events}"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.info("CustomBundlePackedConsumer received record on topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());
        inboxService.handle(record);
        acknowledgment.acknowledge();
    }
}
