package org.hvtra.catalog.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.catalog.service.bundle.CustomerBundleCatalogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Tiêu thụ event ORDER_COMPLETED để tự động lưu Product CUSTOMER, Variant & BOM sau khi đơn bundle hoàn tất. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class CatalogOrderCompletedConsumer {

    private final CatalogOrderCompletedInboxService inboxService;
    private final CustomerBundleCatalogService customerBundleCatalogService;

    @KafkaListener(
            topics = "${catalog.order-events.topics.order-completed:order.completed}",
            groupId = "${kafka-consumer-config.group-id:catalog-service-order-events}"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        inboxService.handle(record, customerBundleCatalogService::persistCompletedOrder);
        acknowledgment.acknowledge();
    }
}
