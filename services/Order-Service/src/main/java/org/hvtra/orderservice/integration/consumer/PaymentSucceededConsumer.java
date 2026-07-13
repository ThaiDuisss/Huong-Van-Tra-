package org.hvtra.orderservice.integration.consumer;

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
public class PaymentSucceededConsumer {

    private final PaymentSucceededInboxService paymentSucceededInboxService;

    @KafkaListener(
            topics = {
                    "${order.payment.events.topics.waiting:payment.waiting-payment}",
                    "${order.payment.events.topics.succeeded:payment.succeeded}",
                    "${order.payment.events.topics.failed:payment.failed}",
                    "${order.payment.events.topics.expired:payment.expired}",
                    "${order.payment.events.topics.cancelled:payment.cancelled}",
                    "${order.payment.events.topics.need-review:payment.need-review}"
            },
            groupId = "${spring.application.name:order-service}-payment-events"
//            containerFactory = "paymentEventKafkaListenerContainerFactory"
    )
    public void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.info("PaymentSucceededConsumer received: {}", record);
        paymentSucceededInboxService.handle(record);
        acknowledgment.acknowledge();
    }
}
