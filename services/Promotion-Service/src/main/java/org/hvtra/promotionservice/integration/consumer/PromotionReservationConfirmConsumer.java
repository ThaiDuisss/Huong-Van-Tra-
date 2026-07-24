package org.hvtra.promotionservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class PromotionReservationConfirmConsumer {

    private final PromotionReservationConfirmInboxService promotionReservationConfirmInboxService;

    @KafkaListener(
            topics = "${promotion.order.events.topics.usage-commit:promotion.usage-commit.requested}",
            groupId = "${spring.application.name:promotion-service}-order-events"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        promotionReservationConfirmInboxService.handle(record);
        acknowledgment.acknowledge();
    }
}
