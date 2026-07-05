package org.hvtra.paymentservice.outbox.resolver;

import org.hvtra.commonservice.kafka.outbox.resolver.OutboxTopicResolver;
import org.hvtra.paymentservice.outbox.event.PaymentEventType;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutboxTopicResolver implements OutboxTopicResolver {

    @Override
    public String resolveTopic(String eventType) {
        return switch (eventType) {
            case PaymentEventType.PAYMENT_WAITING_PAYMENT -> "payment.waiting-payment";
            case PaymentEventType.PAYMENT_SUCCEEDED -> "payment.succeeded";
            case PaymentEventType.PAYMENT_FAILED -> "payment.failed";
            case PaymentEventType.PAYMENT_EXPIRED -> "payment.expired";
            case PaymentEventType.PAYMENT_CANCELLED -> "payment.cancelled";
            case PaymentEventType.PAYMENT_NEED_REVIEW -> "payment.need-review";
            default -> throw new IllegalArgumentException("Unknown payment event type: " + eventType);
        };
    }
}