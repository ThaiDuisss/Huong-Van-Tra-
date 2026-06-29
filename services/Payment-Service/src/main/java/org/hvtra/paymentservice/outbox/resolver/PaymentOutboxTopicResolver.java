package org.hvtra.paymentservice.outbox.resolver;

import org.hvtra.commonservice.kafka.outbox.resolver.OutboxTopicResolver;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutboxTopicResolver implements OutboxTopicResolver {

    @Override
    public String resolveTopic(String eventType) {
        return switch (eventType) {
            case "PAYMENT_SUCCEEDED" -> "payment.succeeded";
            case "PAYMENT_FAILED" -> "payment.failed";
            case "PAYMENT_EXPIRED" -> "payment.expired";
            case "PAYMENT_CANCELLED" -> "payment.cancelled";
            case "PAYMENT_NEED_REVIEW" -> "payment.need-review";
            default -> throw new IllegalArgumentException("Unknown payment event type: " + eventType);
        };
    }
}